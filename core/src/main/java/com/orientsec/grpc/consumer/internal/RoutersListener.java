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

import com.orientsec.grpc.common.constant.RegistryConstants;
import com.orientsec.grpc.consumer.model.ServiceProvider;
import com.orientsec.grpc.consumer.routers.ConditionRouter;
import com.orientsec.grpc.consumer.routers.Router;
import com.orientsec.grpc.consumer.watch.ConsumerListener;
import com.orientsec.grpc.registry.common.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由规则监听器
 *
 * @author dengjianqian
 * @since 2018-8-10 modify by sxp 修正bug：当客户端处在黑名单中，启动客户端后再将黑名单拿掉，客户端还是不能连上服务端
 */
public class RoutersListener extends  AbstractListener implements ConsumerListener {

  private boolean initData;
  public RoutersListener(){
      initData = true;
  }

  @Override
  public void notify(List<URL> urls) {
    List<Router> routes = new ArrayList<Router>();
    for (URL url : urls) {
      if (!RegistryConstants.ROUTER_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
        continue;
      }
      Router router = new ConditionRouter(url);
      routes.add(router);
    }
    Collections.sort(routes);
    zookeeperNameResolver.getRoutes().clear();
    zookeeperNameResolver.setRoutes(routes);



    Map<String, ServiceProvider> providers = zookeeperNameResolver.getServiceProviderMap();
    if (providers == null || providers.size() == 0) {
      String serviceName = zookeeperNameResolver.getServiceName();
      Object lock = getZookeeperNameResolver().getLock();
      synchronized (lock) {
        zookeeperNameResolver.getAllByName(serviceName);
      }
    }

    // 路由规则变化后，重置providersForLoadBalance
    getZookeeperNameResolver().setProvidersForLoadBalance(new ConcurrentHashMap<String, ServiceProvider>());
    getZookeeperNameResolver().setProvidersForLoadBalanceFlag(0);

    //第一次调用时(订阅时)不刷新providers缓存
    if (!initData){
      zookeeperNameResolver.resolveServerInfoWithLock();
    }
    initData = false;
  }
}
