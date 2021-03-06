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
package org.infinispan.config;

import org.infinispan.Cache;
import org.infinispan.interceptors.InvocationContextInterceptor;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Test(testName = "config.CustomInterceptorConfigTest", groups = "functional")
public class CustomInterceptorConfigTest extends AbstractInfinispanTest {
   Cache c;
   CacheContainer cm;

   public void testCustomInterceptors() throws IOException {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<infinispan\n" +
            "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "      xsi:schemaLocation=\"urn:infinispan:config:4.0 http://www.infinispan.org/schemas/infinispan-config-4.0.xsd\"\n" +
            "      xmlns=\"urn:infinispan:config:4.0\">" +
            "<default><customInterceptors> \n" +
            "<interceptor after=\""+ InvocationContextInterceptor.class.getName()+"\" class=\""+DummyInterceptor.class.getName()+"\"/> \n" +
            "</customInterceptors> </default></infinispan>";

      InputStream stream = new ByteArrayInputStream(xml.getBytes());
      cm = TestCacheManagerFactory.fromStream(stream);
      c = cm.getCache();
      DummyInterceptor i = TestingUtil.findInterceptor(c, DummyInterceptor.class);
      assert i != null;
   }

   public void testCustomInterceptorsProgramatically() {
      Configuration cfg = new Configuration();
      cfg.setLockAcquisitionTimeout(1010);
      CustomInterceptorConfig cic = new CustomInterceptorConfig(new DummyInterceptor(), true, false, -1, "", "");
      cfg.setCustomInterceptors(Collections.singletonList(cic));
      cm = new DefaultCacheManager(cfg);
      c = cm.getCache();
      DummyInterceptor i = TestingUtil.findInterceptor(c, DummyInterceptor.class);
      assert i != null;
   }


   public void testCustomInterceptorsProgramaticallyWithOverride() {
      Configuration cfg = new Configuration();
      cfg.setLockAcquisitionTimeout(1010);
      CustomInterceptorConfig cic = new CustomInterceptorConfig(new DummyInterceptor(), true, false, -1, "", "");
      cfg.setCustomInterceptors(Collections.singletonList(cic));
      cm = new DefaultCacheManager(new Configuration());
      ((EmbeddedCacheManager) cm).defineConfiguration("custom", cfg);
      c = cm.getCache("custom");
      DummyInterceptor i = TestingUtil.findInterceptor(c, DummyInterceptor.class);
      assert i != null;
   }

   @AfterMethod
   public void tearDown() {
      if (cm != null) cm.stop();
   }

   public static class DummyInterceptor extends CommandInterceptor {

   }
}


