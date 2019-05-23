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
package com.orientsec.grpc.common.constant;

/**
 * 全局常量
 *
 * @author sxp
 * @since V1.0 2017/3/21
 */
public class GlobalConstants {
  /**
   * 自定义配置文件所在目录的路径的"系统环境变量key值"
   */
  public static final String SYSTEM_PATH_NAME = "dfzq.grpc.config";

  /**
   * 换行符
   */
  public static final String NEW_LINE = "\n";

  /**
   * 配置文件位置
   */
  public static final String CONFIG_FILE_PATH = "dfzq-grpc-config.properties";

  /**
   * 系统模块开关配置文件位置
   */
  public static final String SWITCH_CONFIG_FILE_PATH = "dfzq-switch.properties";

  /**
   * 服务端注册策略在配置文件中的key值
   */
  public static final String PROVIDER_REGISTRY_STRATEGY = "provider.registry.strategy";

  /**
   * 服务端口
   */
  public static final String PROVIDER_SERVICE_PORT = "PROVIDER_SERVICE_PORT";

  /**
   * 客户端请求端口
   */
  public static final String CONSUMER_REQUEST_PORT = "CONSUMER_REQUEST_PORT";

  /**
   * 注册中心地址信息：包括IP:PORT
   */
  public static final String REGISTRY_CENTTER_ADDRESS = "zookeeper.host.server";

  /**
   * 注册中心连接超时时间
   */
  public static final String REGISTRY_CONNECTIONTIMEOUT = "zookeeper.connectiontimeout";

  /**
   * 注册中心会话超时时间
   */
  public static final String REGISTRY_SESSIONTIMEOUT = "zookeeper.sessiontimeout";

  /**
   * 注册中心断线重连最长时间
   */
  public static final String REGISTRY_RETRY_TIME = "zookeeper.retry.time";

  /**
   * 访问控制列表用户名
   */
  public static final String ACL_USERNAME = "zookeeper.acl.username";

  /**
   * 访问控制列表密码
   */
  public static final String ACL_PASSWORD = "zookeeper.acl.password";

  /**
   * 配置文件中的应用名称的key值
   */
  public static final String COMMON_APPLICATION = "common.application";

  /**
   * 配置文件中的grpc版本号的key值
   */
  public static final String COMMON_GRPC = "common.grpc";

  /**
   * 配置文件中的"服务注册使用的IP地址"的key值
   */
  public static final String COMMON_LOCALHOST_IP = "common.localhost.ip";

  /**
   * PROVIDER端服务分组
   */
  public static final String PROVIDER_GROUP = "provider.group";

  /**
   * 配置文件服务版本对应键。
   */
  public static final String PROVIDER_VERSION = "provider.version";

  /**
   * 消费端应用版本获取键。
   */
  public static final String CONSUMER_APPLICATION_VERSION = "consumer.application.version";

  /**
   * 消费端注册策略在配置文件中的key值
   */
  public static final String CONSUMER_REGISTRY_STRATEGY = "consumer.registry.strategy";

  /**
   * 名称
   */
  public static final String NAME = "name";

  /**
   * 服务链中属性chainId中各个数字之间的分隔符
   */
  public static final String CHAINID_SEPARATOR = ".";

  /**
   * 配置文件中的project的key值
   */
  public static final String COMMON_PROJECT = "common.project";

  /**
   * 配置文件中的owner的key值
   */
  public static final String COMMON_OWNER = "common.owner";

  /**
   * 配置文件中的服务注册根路径的key值
   */
  public static final String COMMON_ROOT = "common.root";

  public static final String LOAD_BALANCE_EMPTY_METHOD = "*";


  public enum LB_STRATEGY {
    PICK_FIRST("pick_first"),
    ROUND_ROBIN("round_robin"),
    WEIGHT_ROUND_ROBIN("weight_round_robin"),
    CONSISTENT_HASH("consistent_hash");

    private String simpleName;

    LB_STRATEGY(String simpleName) {
      this.simpleName = simpleName;
    }

    public String getSimpleName() {
      return simpleName;
    }
  }

  public static LB_STRATEGY string2LB(String strategy) {
    LB_STRATEGY ret;

    if ("pick_first".equalsIgnoreCase(strategy)) {
      ret = LB_STRATEGY.PICK_FIRST;
    } else if ("round_robin".equalsIgnoreCase(strategy)) {
      ret = LB_STRATEGY.ROUND_ROBIN;
    } else if ("weight_round_robin".equalsIgnoreCase(strategy)) {
      ret = LB_STRATEGY.WEIGHT_ROUND_ROBIN;
    } else if ("consistent_hash".equalsIgnoreCase(strategy)) {
      ret = LB_STRATEGY.CONSISTENT_HASH;
    } else {
      ret = LB_STRATEGY.ROUND_ROBIN;
    }
    return ret;
  }

  public enum CLUSTER {
    FAILOVER, FAILFAST, FAILBACK, FORKING
  }

  public static CLUSTER string2Cluster(String cluster) {
    CLUSTER ret;
    if (cluster.equalsIgnoreCase("failover")) {
      ret = CLUSTER.FAILOVER;
    } else if (cluster.equalsIgnoreCase("failfast")) {
      ret = CLUSTER.FAILFAST;
    } else if (cluster.equalsIgnoreCase("failback")) {
      ret = CLUSTER.FAILBACK;
    } else {
      ret = CLUSTER.FORKING;
    }
    return ret;
  }

  public static class ServiceAddrKey {
    public static class ZOOKEEPER {
      public static final String PROTOCOL_PREFIX = "zookeeper";
      public static final int DEFAULT_PORT = 2181;
    }

  }

  public static class CommonKey {

    /**
     * 配置信息中“接口”的key值
     */
    public static final String INTERFACE = "interface";

    /**
     * 配置信息中“默认连接数”的key值
     */
    public static final String DEFAULT_CONNECTION = "default.connections";

    /**
     * 并发请求数
     */
    public static final String DEFAULT_REQUESTS = "default.requests";

    /**
     * 配置信息中“时间戳”的key值
     */
    public static final String TIMESTAMP = "timestamp";

    /**
     * 配置信息中“进程ID”的key值
     */
    public static final String PID = "pid";

    /**
     * 配置信息中“表示角色”的key值
     */
    public static final String SIDE = "side";

    /**
     * 配置信息中“服务方法列表”的key值
     */
    public static final String METHODS = "methods";

    /**
     * 配置信息中“类别”的key值
     */
    public static final String CATEGORY = "category";

    /**
     * 配置信息中“应用”的key值
     */
    public static final String APPLICATION = "application";

    /**
     * 配置信息中“grpc”的key值
     */
    public static final String GRPC = "grpc";

    /**
     * 配置信息中“版本”的key值
     */
    public static final String VERSION = "version";

    /**
     * 配置信息中“集群切换方式”的key值
     */
    public static final String DEFAULT_CLUSTER = "default.cluster";

    /**
     * 配置信息中“调用服务负责人”的key值
     */
    public static final String OWNER = "owner";

    /**
     * 配置信息中“负载均衡算法”的key值
     */
    public static final String DEFAULT_LOADBALANCE = "default.loadbalance";

    /**
     * 配置信息中“grpc”的key值
     */
    public static final String GRPC_PROTOCOL = "grpc";

    /**
     * 配置信息中“project”的key值
     */
    public static final String PROJECT = "project";

  }

  /**
   * 服务提供者的常量
   *
   * @author sxp
   * @since V1.0 2017-3-29
   */
  public static class Provider {
    /**
     * 服务端default.requests参数的默认值（缺省值）
     */
    public static final int DEFAULT_REQUESTS_NUM = 2000;

    /**
     * 服务端default.connections参数的默认值（缺省值）
     */
    public static final int DEFAULT_CONNECTIONS_NUM = 20;

    /**
     * Key值相关的常量
     */
    public static class Key extends CommonKey {
      /**
       * 服务是否过时
       */
      public static final String DEPRECATED = "deprecated";

      /**
       * 服务权重
       */
      public static final String WEIGHT = "weight";

      /**
       * 服务是否处于访问保护状态
       */
      public static final String ACCESS_PROTECTED = "access.protected";

    }
  }


  /**
   * 服务消费者的常量
   *
   * @author dengjq
   * @since V1.0 2017-3-31
   */
  public static class Consumer {

    /**
     * Key值相关的常量
     */
    public static class Key extends CommonKey {
      /**
       * 服务版本号（服务提供者的版本号）---- 需要注册
       */
      public static final String SERVICE_VERSION = "service.version";

      /**
       * 负载均衡模式的key值 ---- 不需要注册
       */
      public static final String LOADBALANCE_MODE = "consumer.loadbalance.mode";

      /**
       * 负载均衡模式的key值 ---- 客户端监听注册中心数据变化使用
       */
      public static final String LOADBALANCE_MODE_FOR_LISTENER= "loadbalance.mode";

      public static final String METHOD = "method";

      public static final String DEFAULT_REQUESTS = "consumer.default.requests";

      /**
       * 连续多少次请求出错，自动切换到提供相同服务的新服务器
       */
      public static final String SWITCHOVER_THRESHOLD = "consumer.switchover.threshold";

      /**
       * 服务提供者不可用时的惩罚时间
       */
      public static final String PUNISH_TIME = "consumer.unavailable.provider.punish.time";

      /**
       * 负载均衡策略选择是consistent_hash(一致性Hash)，配置进行hash运算的参数名称的列表
       */
      public static final String HASH_ARGUMENTS = "consumer.consistent.hash.arguments";

      /**
       * grpc断线重连指数回退协议"随机抖动因子"参数
       */
      public static final String BACKOFF_INITIAL = "consumer.backoff.initial";

      /**
       * grpc断线重连指数回退协议"失败重试等待时间上限"参数
       */
      public static final String BACKOFF_MAX = "consumer.backoff.max";

      /**
       * grpc断线重连指数回退协议"下一次失败重试等待时间乘以的倍数"参数
       */
      public static final String BACKOFF_MULTIPLIER = "consumer.backoff.multiplier";

      /**
       * grpc断线重连指数回退协议"随机抖动因子"参数
       */
      public static final String BACKOFF_JITTER = "consumer.backoff.jitter";

      /**
       * 配置文件中的远程服务失败重试次数
       */
      public static final String CONSUME_RDEFAULT_RETRIES = "consumer.default.retries";
    }
  }

  /**
   * dfzq-switch配置文件相关的常量
   */
  public static class Switch {

    /**
     * Key值相关的常量
     */
    public static class Key {
      /**
       * 是否为调试模式
       */
      public static final String DEBUG_ENABLED = "debug.enabled";

      /**
       * 调试模式下的日志开关
       */
      public static final String LOG_SWITCH = "log.switch";

      /**
       * 是否启用provider的功能
       */
      public static final String PROVIDER_ENABLED = "provider.enabled";

      /**
       * 是否启用consumer的功能
       */
      public static final String CONSUMER_ENABLED = "consumer.enabled";

      /**
       * 是否启用kafka日志写入功能
       */
      public static final String WRITEKAFKA_ENABLED = "writekafka.enabled";

      /**
       * 是否打印服务链信息
       */
      public static final String PRINTSERVICECHAIN_ENABLED = "printServiceChain.enabled";
    }
  }

}
