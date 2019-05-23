/*
 * Copyright 2019 Orient Securities Co., Ltd.
 * Copyright 2019 BoCloud Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.orientsec.grpc.consumer.internal;

import com.orientsec.grpc.consumer.model.ServiceProvider;
import com.orientsec.grpc.consumer.watch.ConsumerListener;
import com.orientsec.grpc.registry.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端对服务提供者列表的监听
 */
public class ProvidersListener extends AbstractListener implements ConsumerListener {
  private static final Logger logger = LoggerFactory.getLogger(ProvidersListener.class);
  private boolean initData;
  private boolean isProviderListEmpty = true;

  public ProvidersListener() {
    initData = true;
  }

  @Override
  public void notify(List<URL> urls) {
    Map<String, ServiceProvider> newProviders = getProvidersByUrls(urls);
    int newSize = newProviders.size();

    if (newSize == 0) {
      isProviderListEmpty = true;
    } else {
      isProviderListEmpty = false;
    }

    String serviceName = zookeeperNameResolver.getServiceName();
    logger.info("监听到{}客户端的服务器列表发生变化，当前服务端的个数为{}", serviceName, newSize);

    Map<String, ServiceProvider> services = zookeeperNameResolver.getServiceProviderMap();

    Object lock = zookeeperNameResolver.getLock();
    synchronized (lock) {
      if (services != null) {
        services.clear();
      } else {
        services = new ConcurrentHashMap<String, ServiceProvider>();
      }
      services.putAll(newProviders);

      // 服务列表变化后，重置providersForLoadBalance
      zookeeperNameResolver.setProvidersForLoadBalance(new ConcurrentHashMap<String, ServiceProvider>());
      zookeeperNameResolver.setProvidersForLoadBalanceFlag(0);
    }

    //第一次调用时(订阅时)不刷新providers缓存
    if (!initData) {
      zookeeperNameResolver.resolveServerInfoWithLock();
    }

    initData = false;
  }

  /**
   * 根据监听到的URL组装服务提供者
   *
   * @author sxp
   * @since 2018-5-27
   */
  private Map<String, ServiceProvider> getProvidersByUrls(List<URL> urls) {
    // 客户端指定的服务的版本
    String serviceVersion = zookeeperNameResolver.getServiceVersion();
    if (serviceVersion == null) {
      serviceVersion = "";
    }

    Map<String, ServiceProvider> newProviders = ZookeeperNameResolver.getProvidersByUrls(urls, serviceVersion);
    zookeeperNameResolver.resetAllProviders(newProviders);// 备份：当前服务接口的所有提供者

    return newProviders;
  }

  public boolean isProviderListEmpty() {
    return isProviderListEmpty;
  }
}
