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
package org.infinispan.atomic;

import org.infinispan.Cache;
import org.infinispan.config.Configuration;
import static org.infinispan.context.Flag.SKIP_LOCKING;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextContainer;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

@Test(groups = "functional", testName = "atomic.AtomicMapFunctionalTest")
public class AtomicMapFunctionalTest extends AbstractInfinispanTest {
   private static final Log log = LogFactory.getLog(AtomicMapFunctionalTest.class);
   Cache<String, Object> cache;
   TransactionManager tm;
   private EmbeddedCacheManager cm;

   @BeforeMethod
   @SuppressWarnings("unchecked")
   public void setUp() {
      Configuration c = new Configuration();
      // these 2 need to be set to use the AtomicMapCache
      c.setInvocationBatchingEnabled(true);
      cm = TestCacheManagerFactory.createCacheManager(c, true);
      cache = cm.getCache();
      tm = TestingUtil.getTransactionManager(cache);
   }

   @AfterMethod
   public void tearDown() {
      TestingUtil.killCacheManagers(cm);
      cache = null;
      tm = null;
   }

   public void testChangesOnAtomicMap() {
      AtomicMap<String, String> map = AtomicMapLookup.getAtomicMap(cache, "key");
      assert map.isEmpty();
      map.put("a", "b");
      assert map.get("a").equals("b");

      // now re-retrieve the map and make sure we see the diffs
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a").equals("b");
   }

   public void testTxChangesOnAtomicMap() throws Exception {
      AtomicMap<String, String> map = AtomicMapLookup.getAtomicMap(cache, "key");
      tm.begin();
      assert map.isEmpty();
      map.put("a", "b");
      assert map.get("a").equals("b");
      Transaction t = tm.suspend();

      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a") == null;

      tm.resume(t);
      tm.commit();

      // now re-retrieve the map and make sure we see the diffs
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a").equals("b");
   }

   public void testChangesOnAtomicMapNoLocks() {
      AtomicMap<String, String> map = AtomicMapLookup.getAtomicMap(cache, "key");
      assert map.isEmpty();
      InvocationContextContainer icc = TestingUtil.extractComponent(cache, InvocationContextContainer.class);
      InvocationContext ic = icc.createInvocationContext();
      ic.setFlags(SKIP_LOCKING);
      log.debug("Doing a put");
      assert icc.getInvocationContext().hasFlag(SKIP_LOCKING);
      map.put("a", "b");
      log.debug("Put complete");
      assert map.get("a").equals("b");

      // now re-retrieve the map and make sure we see the diffs
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a").equals("b");
   }

   public void testTxChangesOnAtomicMapNoLocks() throws Exception {
      AtomicMap<String, String> map = AtomicMapLookup.getAtomicMap(cache, "key");
      tm.begin();
      assert map.isEmpty();
      TestingUtil.extractComponent(cache, InvocationContextContainer.class).createInvocationContext().setFlags(SKIP_LOCKING);
      map.put("a", "b");
      assert map.get("a").equals("b");
      Transaction t = tm.suspend();

      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a") == null;

      tm.resume(t);
      tm.commit();

      // now re-retrieve the map and make sure we see the diffs
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a").equals("b");
   }

   public void testChangesOnAtomicMapNoLocksExistingData() {
      AtomicMap<String, String> map = AtomicMapLookup.getAtomicMap(cache, "key");
      assert map.isEmpty();
      map.put("x", "y");
      assert map.get("x").equals("y");
      TestingUtil.extractComponent(cache, InvocationContextContainer.class).createInvocationContext().setFlags(SKIP_LOCKING);
      log.debug("Doing a put");
      map.put("a", "b");
      log.debug("Put complete");
      assert map.get("a").equals("b");
      assert map.get("x").equals("y");

      // now re-retrieve the map and make sure we see the diffs
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("x").equals("y");
      assert AtomicMapLookup.getAtomicMap(cache, "key").get("a").equals("b");
   }
}
