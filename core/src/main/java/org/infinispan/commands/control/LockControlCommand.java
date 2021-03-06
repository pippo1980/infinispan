/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.commands.control;

import org.infinispan.commands.FlagAffectedCommand;
import org.infinispan.commands.Visitor;
import org.infinispan.commands.tx.AbstractTransactionBoundaryCommand;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.TransactionalInvocationContextFlagsOverride;
import org.infinispan.context.impl.RemoteTxInvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LockControlCommand is a command that enables distributed locking across infinispan nodes.
 * <p/>
 * For more details refer to: https://jira.jboss.org/jira/browse/ISPN-70 https://jira.jboss.org/jira/browse/ISPN-48
 *
 * @author Vladimir Blagojevic (<a href="mailto:vblagoje@redhat.com">vblagoje@redhat.com</a>)
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
public class LockControlCommand extends AbstractTransactionBoundaryCommand implements FlagAffectedCommand {

   private static final Log log = LogFactory.getLog(LockControlCommand.class);

   public static final int COMMAND_ID = 3;
   private Set<Object> keys;
   private Object singleKey;
   private boolean implicit = false;
   private boolean unlock = false;
   private Set<Flag> flags;

   public LockControlCommand() {
   }

   public LockControlCommand(Collection<Object> keys, String cacheName, Set<Flag> flags) {
      this(keys, cacheName, flags, false);
   }

   public LockControlCommand(Collection<Object> keys, String cacheName, Set<Flag> flags, boolean implicit) {
      this.cacheName = cacheName;
      this.keys = null;
      this.singleKey = null;
      if (keys != null && !keys.isEmpty()) {
         if (keys.size() == 1) {
            for (Object k: keys) this.singleKey = k;
         } else {
            // defensive copy
            this.keys = new HashSet<Object>(keys);
         }
      }
      this.flags = flags;
      this.implicit = implicit;
   }

   public void attachGlobalTransaction(GlobalTransaction gtx) {
      globalTx = gtx;
   }

   public Set<Object> getKeys() {
      if (keys == null) {
         if (singleKey == null)
            return Collections.emptySet();
         else
            return Collections.singleton(singleKey);
      }

      return keys;
   }

   public void replaceKey(Object oldKey, Object replacement) {
      if (singleKey != null && singleKey.equals(oldKey)) {
         singleKey = replacement;
      } else {
         if (keys != null) {
            if (keys.remove(oldKey)) keys.add(replacement);
         }
      }
   }

   public void replaceKeys(Map<Object, Object> replacements) {
      for (Map.Entry<Object, Object> e: replacements.entrySet()) replaceKey(e.getKey(), e.getValue());
   }

   public boolean multipleKeys() {
      return keys != null && keys.size() > 1;
   }

   public Object getSingleKey() {
      if (singleKey == null) {
         if (keys != null) {
            for (Object sk: keys) return sk;
            return null;
         } else {
            return null;
         }
      } else {
         return singleKey;
      }
   }

   public boolean isImplicit() {
      return implicit;
   }

   public boolean isExplicit() {
      return !isImplicit();
   }

   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitLockControlCommand((TxInvocationContext) ctx, this);
   }

   @Override
   public Object perform(InvocationContext ignored) throws Throwable {
      if (ignored != null)
         throw new IllegalStateException("Expected null context!");

      RemoteTxInvocationContext ctxt = icc.createRemoteTxInvocationContext(getOrigin());
      RemoteTransaction transaction = txTable.getRemoteTransaction(globalTx);

      if (transaction == null) {
         if (unlock) {
            if (log.isTraceEnabled()) {
               log.trace("Unlock for non-existant transaction " + globalTx + ". Not doing anything.");
            }
            return null;
         }
         //create a remote tx without any modifications (we do not know modifications ahead of time)
         transaction = txTable.createRemoteTransaction(globalTx);
      }
      ctxt.setRemoteTransaction(transaction);
      TxInvocationContext ctx = ctxt;
      if (flags != null && !flags.isEmpty()) {
         ctx = new TransactionalInvocationContextFlagsOverride(ctxt, flags);
      }
      return invoker.invoke(ctx, this);
   }

   public byte getCommandId() {
      return COMMAND_ID;
   }

   public Object[] getParameters() {
      if (keys == null || keys.isEmpty()) {
         if (singleKey == null)
            return new Object[]{globalTx, cacheName, unlock, (byte) 1, flags};
         else
            return new Object[]{globalTx, cacheName, unlock, (byte) 2, singleKey, flags};
      }
      return new Object[]{globalTx, cacheName, unlock, (byte) 3, keys, flags};
   }

   @SuppressWarnings("unchecked")
   public void setParameters(int commandId, Object[] args) {
      if (commandId != COMMAND_ID)
         throw new IllegalStateException("Unusupported command id:" + commandId);
      globalTx = (GlobalTransaction) args[0];
      cacheName = (String) args[1];
      unlock = (Boolean) args[2];

      keys = null;
      singleKey = null;
      byte mode = (Byte) args[3];
      switch (mode) {
         case 1:
            if (args.length == 5)
               this.flags = (Set<Flag>) args[4];
            break;
         case 2:
            singleKey = args[4];
            if (args.length == 6)
               this.flags = (Set<Flag>) args[5];
            break;
         case 3:
            keys = (Set<Object>) args[4];
            if (args.length == 6)
               this.flags = (Set<Flag>) args[5];
            break;
      }
   }

   public boolean isUnlock() {
      return unlock;
   }

   public void setUnlock(boolean unlock) {
      this.unlock = unlock;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      LockControlCommand that = (LockControlCommand) o;
      if (!super.equals(that))
         return false;
      return keys.equals(that.keys) && Util.safeEquals(singleKey, that.singleKey) && (unlock == that.unlock);
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (keys != null ? keys.hashCode() : 0);
      return 31 * result + (singleKey != null ? singleKey.hashCode() : 0);
   }

   @Override
   public String toString() {
      return new StringBuilder()
         .append("LockControlCommand{keys=").append(keys)
         .append(", cacheName='").append(cacheName)
         .append(", flags=").append(flags)
         .append("', implicit=").append(implicit)
         .append(", unlock=").append(unlock)
         .append(", singleKey='").append(singleKey)
         .append("'}")
         .toString();
   }

   @Override
   public Set<Flag> getFlags() {
      return flags;
   }

   @Override
   public void setFlags(Set<Flag> flags) {
      this.flags = flags;
   }
}
