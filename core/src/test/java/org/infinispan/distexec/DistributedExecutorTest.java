/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.infinispan.distexec;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.infinispan.Cache;
import org.infinispan.config.Configuration;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.distribution.BaseDistFunctionalTest;
import org.infinispan.manager.DefaultCacheManager;
import org.testng.annotations.Test;

/**
 * Tests org.infinispan.distexec.DistributedExecutorService
 * 
 * @author Vladimir Blagojevic
 */
@Test(groups = "functional", testName = "distexec.DistributedExecutorTest")
public class DistributedExecutorTest extends BaseDistFunctionalTest {

   public DistributedExecutorTest() {
   }

   protected void createCacheManagers() throws Throwable {
      super.createCacheManagers();
   }

   public void testBasicInvocation() throws Exception {

      DistributedExecutorService des = new DefaultExecutorService(c1);
      Future<Integer> future = des.submit(new SimpleCallable());

      Integer r = future.get();
      assert r == 1;
   } 
   
   @Test(expectedExceptions = { IllegalStateException.class })
   public void testImproperCacheStateForDistribtuedExecutor() {
      GlobalConfiguration gc = GlobalConfiguration.getNonClusteredDefault();
      Configuration c = new Configuration();
      DefaultCacheManager defaultCacheManager = new DefaultCacheManager(gc, c, true);
      Cache<Object, Object> cache = defaultCacheManager.getCache();
      DistributedExecutorService des = new DefaultExecutorService(cache);
   }

   public void testExceptionInvocation() throws Exception {

      DistributedExecutorService des = new DefaultExecutorService(c1);

      Future<Integer> future = des.submit(new ExceptionThrowingCallable());
      int exceptionCount = 0;
      try {
         future.get();
         throw new IllegalStateException("Should not have reached this code");
      } catch (ExecutionException ex) {
         exceptionCount++;
      }
      assert exceptionCount == 1;

      List<Future<Integer>> list = des.submitEverywhere(new ExceptionThrowingCallable());
      exceptionCount = 0;
      for (Future<Integer> f : list) {
         try {
            f.get();
            throw new IllegalStateException("Should not have reached this code");
         } catch (ExecutionException ex) {
            exceptionCount++;
         }
      }
      assert exceptionCount == list.size();
   }

   public void testRunnableInvocation() throws Exception {

      DistributedExecutorService des = new DefaultExecutorService(c1);

      Future<?> future = des.submit(new BoringRunnable());
      Object object = future.get();
      assert object == null;

      des.execute(new BoringRunnable());
      int exceptionCount = 0;
      try {
         des.execute(new Runnable() {
            @Override
            public void run() {
            }
         });
         throw new Exception("Should not have happened");
      } catch (IllegalArgumentException iae) {
         exceptionCount++;
      }

      assert exceptionCount == 1;
   }
   
   /**
    * Tests Callable isolation as it gets invoked across the cluster
    * https://issues.jboss.org/browse/ISPN-1041
    * 
    * @throws Exception
    */
   public void testCallableIsolation() throws Exception {
      DefaultExecutorService des = new DefaultExecutorService(c1);

      List<Future<Integer>> list = des.submitEverywhere(new SimpleCallableWithField());
      assert list != null && !list.isEmpty();
      for (Future<Integer> f : list) {
         assert f.get() == 0 ;
      }
   }

   public void testTaskCancellation() throws Exception {

      DistributedExecutorService des = new DefaultExecutorService(c2);

      Future<Integer> future = des.submit(new SimpleCallable());
      future.cancel(true);
      int exceptionCount = 0;
      try {
         future.get();
         throw new IllegalStateException("Should not have reached this code");
      } catch (Exception e) {
         exceptionCount ++;
         assert e instanceof CancellationException;
      }
      
      assert exceptionCount ==1;
   }

   public void testBasicDistributedCallable() throws Exception {

      DistributedExecutorService des = new DefaultExecutorService(c2);
      Future<Boolean> future = des.submit(new SimpleDistributedCallable(false));
      Boolean r = future.get();
      assert r;
   }

   public void testBasicDistributedCallableWitkKeys() throws Exception {
      c1.put("key1", "Manik");
      c1.put("key2", "Mircea");
      c1.put("key3", "Galder");
      c1.put("key4", "Sanne");

      DistributedExecutorService des = new DefaultExecutorService(c1);

      Future<Boolean> future = des.submit(new SimpleDistributedCallable(true), new String[] {
               "key1", "key2" });
      Boolean r = future.get();
      assert r;
   }

   public void testDistributedCallableEverywhereWithKeys() throws Exception {
      c1.put("key1", "Manik");
      c1.put("key2", "Mircea");
      c1.put("key3", "Galder");
      c1.put("key4", "Sanne");

      DefaultExecutorService des = new DefaultExecutorService(c1);

      List<Future<Boolean>> list = des.submitEverywhere(new SimpleDistributedCallable(true),
               new String[] { "key1", "key2" });
      assert list != null && !list.isEmpty();
      for (Future<Boolean> f : list) {
         assert f.get();
      }
   }

   public void testDistributedCallableEverywhere() throws Exception {

      DefaultExecutorService des = new DefaultExecutorService(c1);

      List<Future<Boolean>> list = des.submitEverywhere(new SimpleDistributedCallable(false));
      assert list != null && !list.isEmpty();
      for (Future<Boolean> f : list) {
         assert f.get();
      }
   }

   static class SimpleDistributedCallable implements DistributedCallable<String, String, Boolean>,
            Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = 623845442163221832L;
      private boolean invokedProperly = false;
      private final boolean hasKeys;

      public SimpleDistributedCallable(boolean hasKeys) {
         this.hasKeys = hasKeys;
      }

      @Override
      public Boolean call() throws Exception {
         return invokedProperly;
      }

      @Override
      public void setEnvironment(Cache<String, String> cache, Set<String> inputKeys) {
         boolean keysProperlySet = hasKeys ? inputKeys != null && !inputKeys.isEmpty()
                  : inputKeys != null && inputKeys.isEmpty();
         invokedProperly = cache != null && keysProperlySet;
      }

      public boolean validlyInvoked() {
         return invokedProperly;
      }
   }

   static class SimpleCallable implements Callable<Integer>, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = -8589149500259272402L;

      @Override
      public Integer call() throws Exception {
         return 1;
      }
   }
   
   static class SimpleCallableWithField implements Callable<Integer>, Serializable {
      
      /** The serialVersionUID */
      private static final long serialVersionUID = -6262148927734766558L;
      private int count; 

      @Override
      public Integer call() throws Exception {
         return count++;
      }
   }

   static class ExceptionThrowingCallable implements Callable<Integer>, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = -8682463816319507893L;

      @Override
      public Integer call() throws Exception {
         throw new Exception("Intenttional Exception from ExceptionThrowingCallable");
      }
   }

   static class BoringRunnable implements Runnable, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = 6898519516955822402L;

      @Override
      public void run() {
         System.out.println("I am a boring runnable");
      }

   }
}