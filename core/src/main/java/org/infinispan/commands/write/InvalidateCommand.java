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
package org.infinispan.commands.write;

import org.infinispan.commands.Visitor;
import org.infinispan.context.InvocationContext;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Arrays;
import java.util.Collection;


/**
 * Removes an entry from memory - never removes the entry.
 *
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
public class InvalidateCommand extends RemoveCommand {
   public static final int COMMAND_ID = 6;
   private static final Log log = LogFactory.getLog(InvalidateCommand.class);
   private static final boolean trace = log.isTraceEnabled();
   protected Object[] keys;

   public InvalidateCommand() {
   }

   public InvalidateCommand(CacheNotifier notifier, Object... keys) {
      this.keys = keys;
      this.notifier = notifier;
   }

   public InvalidateCommand(CacheNotifier notifier, Collection<Object> keys) {
      if (keys == null || keys.isEmpty())
         this.keys = new Object[]{};
      else
         this.keys = keys.toArray(new Object[keys.size()]);
      this.notifier = notifier;
   }

   /**
    * Performs an invalidation on a specified entry
    *
    * @param ctx invocation context
    * @return null
    */
   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      if (trace) {
         log.trace("Invalidating keys %s", Arrays.toString(keys));
      }
      for (Object k : keys) {
         invalidate(ctx, k);
      }
      return null;
   }

   protected void invalidate(InvocationContext ctx, Object keyToInvalidate) throws Throwable {
      key = keyToInvalidate; // so that the superclass can see it
      log.warn("Invalidating key %s", keyToInvalidate);
      super.perform(ctx);
   }

   @Override
   protected void notify(InvocationContext ctx, Object value, boolean isPre) {
      notifier.notifyCacheEntryInvalidated(key, value, isPre, ctx);
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "InvalidateCommand{keys=" +
            Arrays.toString(keys) +
            '}';
   }

   @Override
   public Object[] getParameters() {
      if (keys == null || keys.length == 0) {
         return new Object[]{0};
      } else if (keys.length == 1) {
         return new Object[]{1, keys[0]};
      } else {
         Object[] retval = new Object[keys.length + 1];
         retval[0] = keys.length;
         System.arraycopy(keys, 0, retval, 1, keys.length);
         return retval;
      }
   }

   @Override
   public void setParameters(int commandId, Object[] args) {
      if (commandId != COMMAND_ID) throw new IllegalStateException("Invalid method id");
      int size = (Integer) args[0];
      keys = new Object[size];
      if (size == 1) {
         keys[0] = args[1];
      } else if (size > 0) {
         System.arraycopy(args, 1, keys, 0, size);
      }
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitInvalidateCommand(ctx, this);
   }

   @Override
   public Object getKey() {
      throw new UnsupportedOperationException("Not supported.  Use getKeys() instead.");
   }

   public Object[] getKeys() {
      return keys;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof InvalidateCommand)) {
         return false;
      }
      if (!super.equals(o)) {
         return false;
      }

      InvalidateCommand that = (InvalidateCommand) o;

      if (!Arrays.equals(keys, that.keys)) {
         return false;
      }
      return true;
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (keys != null ? Arrays.hashCode(keys) : 0);
      return result;
   }
}