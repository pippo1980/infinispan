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
package org.infinispan.loaders.cluster;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.InternalCacheValue;
import org.infinispan.context.InvocationContext;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.loaders.AbstractCacheLoader;
import org.infinispan.loaders.CacheLoaderConfig;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheLoaderMetadata;
import org.infinispan.marshall.StreamingMarshaller;
import org.infinispan.remoting.responses.ClusteredGetResponseValidityFilter;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * Cache loader that consults other members in the cluster for values. A <code>remoteCallTimeout</code> property is
 * required, a <code>long</code> that specifies in milliseconds how long to wait for results before returning a null.
 *
 * @author Mircea.Markus@jboss.com
 */
@CacheLoaderMetadata(configurationClass = ClusterCacheLoaderConfig.class)
public class ClusterCacheLoader extends AbstractCacheLoader {
   private static final Log log = LogFactory.getLog(ClusterCacheLoader.class);

   private ClusterCacheLoaderConfig config;
   private RpcManager rpcManager;
   private DistributionManager distributionManager;
   private AdvancedCache cache;

   public void init(CacheLoaderConfig config, Cache cache, StreamingMarshaller m) {
      this.config = (ClusterCacheLoaderConfig) config;
      this.cache = cache.getAdvancedCache();
      rpcManager = this.cache.getRpcManager();
      distributionManager = this.cache.getDistributionManager();
   }

   public InternalCacheEntry load(Object key) throws CacheLoaderException {
      if (!(isCacheReady() && isLocalCall())) return null;
      ClusteredGetCommand clusteredGetCommand = new ClusteredGetCommand(key, cache.getName());
      Collection<Response> responses = doRemoteCall(clusteredGetCommand);
      if (responses.isEmpty()) return null;

      Response response;
      if (responses.size() > 1) {
         // Remove duplicates before deciding if multiple responses were received
         Set<Response> setResponses = new HashSet(responses);
         if (setResponses.size() > 1)
            throw new CacheLoaderException(String.format(
                  "Responses contains more than 1 element and these elements are not equal, so can't decide which one to use: %s",
                  setResponses));
         response = setResponses.iterator().next();
      } else {
         response = responses.iterator().next();
      }

      if (response.isSuccessful() && response instanceof SuccessfulResponse) {
         InternalCacheValue value = (InternalCacheValue) ((SuccessfulResponse) response).getResponseValue();
         return value.toInternalCacheEntry(key);
      }

      String message = "Unknown responses from remote cache: " + responses;
      log.error(message);
      throw new CacheLoaderException(message);
   }

   @SuppressWarnings(value = "unchecked")
   public Set<InternalCacheEntry> loadAll() throws CacheLoaderException {
      return emptySet();
   }

   public Set<InternalCacheEntry> load(int maxElems) throws CacheLoaderException {
      return emptySet();
   }

   @Override
   public Set<Object> loadAllKeys(Set<Object> keysToExclude) throws CacheLoaderException {
      return emptySet();
   }

   public void start() throws CacheLoaderException {
      //nothing to do here
   }

   public void stop() throws CacheLoaderException {
      //nothing to do here
   }

   public Class<? extends CacheLoaderConfig> getConfigurationClass() {
      return ClusterCacheLoaderConfig.class;
   }

   private Collection<Response> doRemoteCall(ClusteredGetCommand clusteredGetCommand) throws CacheLoaderException {
      Set<Address> validMembers = new HashSet<Address>(rpcManager.getTransport().getMembers());
      validMembers.remove(rpcManager.getTransport().getAddress());
      ResponseFilter filter = new ClusteredGetResponseValidityFilter(validMembers);
      try {
         return rpcManager.invokeRemotely(null, clusteredGetCommand, ResponseMode.WAIT_FOR_VALID_RESPONSE, config.getRemoteCallTimeout(), false, filter).values();
      } catch (Exception e) {
         log.error("error while doing remote call", e);
         throw new CacheLoaderException(e);
      }
   }

   private boolean isLocalCall() {
      InvocationContext invocationContext = cache.getInvocationContextContainer().getInvocationContext();
      return invocationContext.isOriginLocal();
   }

   /**
    * A test to check whether the cache is in its started state.  If not, calls should not be made as the channel may
    * not have properly started, blocks due to state transfers may be in progress, etc.
    *
    * @return true if the cache is in its STARTED state.
    */
   protected boolean isCacheReady() {
      return cache.getStatus() == ComponentStatus.RUNNING;
   }
}
