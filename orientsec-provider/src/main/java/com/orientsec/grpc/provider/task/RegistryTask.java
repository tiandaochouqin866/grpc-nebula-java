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
package com.orientsec.grpc.provider.task;

import com.google.common.base.Preconditions;
import com.orientsec.grpc.common.constant.GlobalConstants;
import com.orientsec.grpc.common.constant.RegistryConstants;
import com.orientsec.grpc.common.exception.BusinessException;
import com.orientsec.grpc.common.model.BusinessResult;
import com.orientsec.grpc.common.model.ConfigFile;
import com.orientsec.grpc.common.resource.SystemConfig;
import com.orientsec.grpc.common.util.ConfigFileHelper;
import com.orientsec.grpc.common.util.IpUtils;
import com.orientsec.grpc.common.util.ProcessUtils;
import com.orientsec.grpc.common.util.StringUtils;
import com.orientsec.grpc.provider.common.ProviderConstants;
import com.orientsec.grpc.provider.core.ProviderServiceRegistryImpl;
import com.orientsec.grpc.provider.core.ServiceConfigUtils;
import com.orientsec.grpc.provider.watch.ProvidersListener;
import com.orientsec.grpc.registry.common.Constants;
import com.orientsec.grpc.registry.common.URL;
import com.orientsec.grpc.registry.service.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 注册服务
 * <p>
 * 服务提供者启动服务时，基于配置文件，自动注册该服务。 <br>
 * 从配置文件中获取服务注册必选参数和部分可选参数，为注册服务做准备。
 * </p>
 *
 * @author sxp
 * @since V1.0 2017/3/23
 */
public class RegistryTask {
  private static final Logger logger = LoggerFactory.getLogger(RegistryTask.class);
  ProviderServiceRegistryImpl caller;


  /**
   * 服务提供者的配置文件信息
   */
  private Map<String, Object> providerConfig = new HashMap<String, Object>(ProviderConstants.CONFIG_CAPACITY);

  /**
   * 该服务器上的服务接口、方法信息等
   */
  private List<Map<String, Object>> servicesParams;

  /**
   * 该服务器上的所有服务接口名
   */
  private List<String> interfaceNames;

  public RegistryTask(ProviderServiceRegistryImpl caller, List<Map<String, Object>> servicesParams) {
    this.caller = caller;
    this.servicesParams = servicesParams;
  }

  /**
   * 主方法
   *
   * @author sxp
   * @since V1.0 2017/3/23
   */
  public void work() throws Exception {
    if (servicesParams == null) {
      throw new BusinessException("注册服务失败:传入的参数servicesParams为空");
    }

    BusinessResult result = getInterfaceNames();
    if (!result.isSuccess()) {
      throw new BusinessException("注册服务失败:" + result.getMessage());
    }

    if (providerConfig == null) {
      throw new BusinessException("注册服务" + interfaceNames + "失败:读取配置文件失败");
    }

    // 读取服务配置文件
    Properties pros = SystemConfig.getProperties();
    if (pros == null) {
      throw new BusinessException("注册服务" + interfaceNames + "失败:读取配置文件失败，请确认属性文件的路径["
              + GlobalConstants.CONFIG_FILE_PATH + "]是否正确！");
    }

    // 校验和保存配置文件信息
    result = checkAndSaveProperties(pros);
    if (!result.isSuccess()) {
      throw new BusinessException("注册服务" + interfaceNames + "失败:" + result.getMessage());
    }

    // 保存当前服务器上所有服务的配置信息
    saveServicesConfig();

    // 注册并增加监听器
    doRegister();
  }

  /**
   * 初始化当前服务器重要属性
   *
   * @author sxp
   * @since V1.0 2017-3-23
   */
  private BusinessResult getInterfaceNames() {
    String key = GlobalConstants.Provider.Key.INTERFACE;
    String value, msg;

    interfaceNames = new ArrayList<String>(servicesParams.size());

    for (Map<String, Object> map : servicesParams) {
      if (map.containsKey(key)) {
        value = (String) map.get(key);
        interfaceNames.add(value);
      } else {
        msg = "传入的参数中servicesParams缺少" + key + "信息！";
        return new BusinessResult(false, msg);
      }
    }

    BusinessResult result = new BusinessResult(true, null);
    return result;
  }

  /**
   * 校验和保存配置文件信息
   *
   * @author sxp
   * @since V1.0 2017-3-23
   */
  private BusinessResult checkAndSaveProperties(Properties pros) {
    String msg;

    if (pros == null || pros.isEmpty()) {
      msg = "配置文件[" + GlobalConstants.CONFIG_FILE_PATH + "]中未配置有效属性！";
      return new BusinessResult(false, msg);
    }

    List<ConfigFile> allConf = ConfigFileHelper.getProvider();
    String proName, value;

    for (ConfigFile conf : allConf) {
      if (ConfigFileHelper.confFileCommonKeys.containsKey(conf.getName())){
        proName = ConfigFileHelper.COMMON_KEY_PREFIX + conf.getName();// 配置文件中的属性名
      } else {
        proName = ConfigFileHelper.PROVIDER_KEY_PREFIX + conf.getName();
      }

      if (pros.containsKey(proName)) {
        value = pros.getProperty(proName);
        if (value != null) {
          value = value.trim();// 去空格
        }

        providerConfig.put(conf.getName(), value);
      } else {
        value = null;

        providerConfig.put(conf.getName(), conf.getDefaultValue());
      }

      // 校验必填项
      if (conf.isRequired() && StringUtils.isEmpty(value)) {
        msg = "配置文件[" + GlobalConstants.CONFIG_FILE_PATH + "]中[" + proName + "]是必填项，属性值不能为空！";
        return new BusinessResult(false, msg);
      }
    }

    // 服务上线时间戳，也即从1970年1月1日（UTC/GMT的午夜）开始所经过的毫秒
    providerConfig.put(GlobalConstants.CommonKey.TIMESTAMP, System.currentTimeMillis());
    // 进程id
    providerConfig.put(GlobalConstants.CommonKey.PID, ProcessUtils.getProcessId());

    BusinessResult result = new BusinessResult(true, null);
    return result;
  }

  /**
   * 保存当前服务的配置信息
   *
   * @author sxp
   * @since V1.0 2017-3-24
   */
  private void saveServicesConfig() {
    Map<String, Map<String, Object>> currentServicesConfig = ServiceConfigUtils.getCurrentServicesConfig();
    Map<String, Map<String, Object>> initialServicesConfig = ServiceConfigUtils.getInitialServicesConfig();

    List<Map<String, Object>> servicesConfig = caller.getServicesConfig();

    List<ConfigFile> allConf = ConfigFileHelper.getProvider();
    Map<String, Object> confItem;
    Object value;
    String interfaceName, confKey;
    String[] keysFromParam = {GlobalConstants.Provider.Key.INTERFACE, GlobalConstants.CommonKey.METHODS,
            GlobalConstants.PROVIDER_SERVICE_PORT};
    String[] keysOfAuto = {GlobalConstants.CommonKey.TIMESTAMP, GlobalConstants.CommonKey.PID};

    for (Map<String, Object> map : servicesParams) {
      interfaceName = null;
      confItem = new LinkedHashMap<>(ProviderConstants.CONFIG_CAPACITY);

      for (String key : keysFromParam) {
        value = (map.containsKey(key)) ? (map.get(key)) : (null);
        confItem.put(key, value);

        if (GlobalConstants.Provider.Key.INTERFACE.equals(key)) {
          interfaceName = (String) value;
        }
      }

      for (String key : keysOfAuto) {
        value = (providerConfig.containsKey(key)) ? (providerConfig.get(key)) : (null);
        confItem.put(key, value);
      }

      for (ConfigFile conf : allConf) {
        confKey = conf.getName();
        value = (providerConfig.containsKey(confKey)) ? (providerConfig.get(confKey)) : (null);
        confItem.put(confKey, value);
      }

      servicesConfig.add(confItem);
      currentServicesConfig.put(interfaceName, confItem);
      initialServicesConfig.put(interfaceName, new LinkedHashMap<>(confItem));
    }
  }

  /**
   * 注册并订阅监听器
   *
   * @author sxp
   * @since V1.0 2017/3/24
   */
  private void doRegister() throws Exception {
    List<Map<String, Object>> servicesConfig = caller.getServicesConfig();
    List<Map<String, Object>> listenersInfo = caller.getListenersInfo();

    Provider provide;
    ProvidersListener listener;
    Map<String, String> providerInfo;
    Map<String, String> parameters;
    Map<String, Object> info;
    Object value;
    String valueOfS, interfaceName, application;
    int port;
    boolean accessProtected;

    provide = new Provider();

    for (Map<String, Object> confItem : servicesConfig) {
      // 将url和监听器缓存起来，服务关闭时需要注销监听器
      info = new HashMap<String, Object>();

      interfaceName = (String) confItem.get(GlobalConstants.Provider.Key.INTERFACE);
      application = (String) confItem.get(GlobalConstants.Provider.Key.APPLICATION);

      Preconditions.checkNotNull(interfaceName, "interfaceName");

      port = ((Integer) confItem.get(GlobalConstants.PROVIDER_SERVICE_PORT)).intValue();

      accessProtected = false;// 缺省值为false,不受访问保护

      providerInfo = new HashMap<String, String>();
      for (Map.Entry<String, Object> entry : confItem.entrySet()) {
        if (GlobalConstants.PROVIDER_SERVICE_PORT.equals(entry.getKey())) {
          continue;
        }

        value = entry.getValue();
        valueOfS = (value == null) ? (null) : String.valueOf(value);
        if (!StringUtils.isEmpty(valueOfS)) {
          providerInfo.put(entry.getKey(), valueOfS);
        }

        if (GlobalConstants.Provider.Key.ACCESS_PROTECTED.equals(entry.getKey())) {
          if (StringUtils.isEmpty(valueOfS)) {
            accessProtected = false;// 缺省值为false,不受访问保护
          } else {
            accessProtected = Boolean.valueOf(valueOfS).booleanValue();
          }
        }
      }

      // 注册服务(providers)
      providerInfo.put(RegistryConstants.CATEGORY_KEY, RegistryConstants.PROVIDERS_CATEGORY);
      parameters = new HashMap<>(providerInfo);

      URL urlOfService = new URL(RegistryConstants.GRPC_PROTOCOL, caller.getIp(), port, parameters);

      logger.info("服务端注册：" + urlOfService);
      info.put("url-service", urlOfService);// 缓存数据
      provide.registerService(urlOfService);

      // 如果该服务处于访问保护状态，在注册服务以后，同时初始化写入一条 “rule==> host != ${provider.host.ip}” 的
      // 路由规则（临时节点），表示禁止所有客户端访问当前注册的服务
      if (accessProtected) {
        URL urlOfRouter = getAcessProtectdUrl(interfaceName);
        provide.registerService(urlOfRouter);
      }

      // 订阅监听器(configurators)
      providerInfo.put(RegistryConstants.CATEGORY_KEY, RegistryConstants.CONFIGURATORS_CATEGORY);
      providerInfo.put(GlobalConstants.CommonKey.VERSION, RegistryConstants.ANY_VALUE);// 不能限制版本
      parameters = new HashMap<>(providerInfo);

      URL urlOfListener = new URL(RegistryConstants.OVERRIDE_PROTOCOL, caller.getIp(), port, parameters);
      listener = new ProvidersListener(interfaceName, caller.getIp(), application);

      logger.info("服务端注册监听器");
      info.put("url-listener", urlOfListener);// 缓存数据
      info.put("listener", listener);// 缓存数据
      provide.subscribe(urlOfListener, listener);

      listenersInfo.add(info);// 缓存数据
    }
  }

  /**
   * 禁止所有客户端访问当前注册的服务的URL
   *
   * @author sxp
   * @since 2018/12/1
   */
  public static URL getAcessProtectdUrl(String interfaceName) {
    // 如果该服务处于访问保护状态，在注册服务以后，同时初始化写入一条 “rule==> host != ${provider.host.ip}” 的
    // 路由规则（临时节点），表示禁止所有客户端访问当前注册的服务

    // route://0.0.0.0/com.foo.BarService?category=routers&dynamic=true&force=true&name=my-rule-001
    // &priority=0&router=condition&rule==> host != 172.22.3.91&runtime=false

    String ip = IpUtils.getIP4WithPriority();
    String name = "access-protected-rule-" + ip;
    String rule = "host = * => host != " + ip;

    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(GlobalConstants.CommonKey.INTERFACE, interfaceName);
    parameters.put(RegistryConstants.CATEGORY_KEY, RegistryConstants.ROUTERS_CATEGORY);
    parameters.put(RegistryConstants.DYNAMIC_KEY, "true");// 临时节点
    parameters.put(Constants.FORCE_KEY, "true");// 当路由结果为空时，是否强制执行
    parameters.put(GlobalConstants.NAME, name);
    parameters.put(Constants.PRIORITY_KEY, String.valueOf(Integer.MAX_VALUE));// 优先级越大越靠前执行
    parameters.put("router", "condition");
    parameters.put(Constants.RULE_KEY, rule);
    parameters.put(Constants.RUNTIME_KEY, "false");

    return new URL(RegistryConstants.ROUTER_PROTOCOL, Constants.ANYHOST_VALUE, 0, parameters);
  }
}
