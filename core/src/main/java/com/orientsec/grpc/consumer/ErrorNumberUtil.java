package com.orientsec.grpc.consumer;

import com.orientsec.grpc.common.constant.GlobalConstants;
import com.orientsec.grpc.common.resource.SystemConfig;
import com.orientsec.grpc.common.util.DateUtils;
import com.orientsec.grpc.common.util.PropertiesUtils;
import com.orientsec.grpc.common.util.StringUtils;
import com.orientsec.grpc.consumer.internal.ProvidersListener;
import com.orientsec.grpc.consumer.internal.ZookeeperNameResolver;
import com.orientsec.grpc.consumer.model.ServiceProvider;
import com.orientsec.grpc.common.collect.ConcurrentHashSet;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.NameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 容错机制
 * <p>
 * 功能：连续多次请求出错，自动切换到提供相同服务的新服务器 <br>
 * 备注：将代码从FailoverUtils中独立出来，便于阅读
 * <p/>
 *
 * @author sxp
 * @since 2019/9/2
 */
public class ErrorNumberUtil {
  private static final Logger logger = LoggerFactory.getLogger(ErrorNumberUtil.class);

  private static final String CONSUMERID_PROVIDERID_SEPARATOR = FailoverUtils.CONSUMERID_PROVIDERID_SEPARATOR;

  /**
   * 连续多少次请求出错，自动切换到提供相同服务的新服务器
   */
  private static int switchoverThreshold = initThreshold();

  private static int initThreshold() {
    String key = GlobalConstants.Consumer.Key.SWITCHOVER_THRESHOLD;
    int defaultValue = 5;
    Properties properties = SystemConfig.getProperties();

    int threshold = PropertiesUtils.getValidIntegerValue(properties, key, defaultValue);
    if (threshold <= 0) {
      threshold = defaultValue;
    }

    logger.info(key + " = " + threshold);

    return threshold;
  }

  /**
   * 服务提供者不可用时的惩罚时间，即多次请求出错的服务提供者一段时间内不再去请求
   * <p>单位为秒,缺省值为60</p>
   */
  private static long punishTime = initPunishTime();// 秒

  private static long punishTimetoMillis = punishTime * 1000;// 毫秒

  private static long initPunishTime() {
    String key = GlobalConstants.Consumer.Key.PUNISH_TIME;
    long defaultValue = 60;
    Properties properties = SystemConfig.getProperties();

    long time = PropertiesUtils.getValidLongValue(properties, key, defaultValue);
    if (time < 0) {// 可以为0
      time = defaultValue;
    }

    logger.info(key + " = " + time);

    return time;
  }


  /**
   * 调用失败的【客户端对应服务提供者列表】
   * <p>
   * key值为：consumerId  <br>
   * value值为: 服务提供者IP:port的列表  <br>
   * 其中consumerId指的是客户端在zk上注册的URL的字符串形式，IP:port指的是服务提供者的IP和端口
   * <p/>
   */
  private volatile static ConcurrentHashMap<String, ConcurrentHashSet<String>> failingProviders = new ConcurrentHashMap<>();

  /**
   * 各个【客户端】最后一个服务提供者的删除时间
   * <p>
   * key值为：consumerId  <br>
   * value值为: 最后一个服务提供者的删除时间，以当时的毫秒时间戳记录   <br>
   * 其中consumerId指的是客户端在zk上注册的URL的字符串形式
   * <p/>
   */
  private volatile static Map<String, Long> removeProviderTimestamp = new HashMap<>();


  /**
   * 各个【客户端对应服务提供者】最后一次服务调用失败时间
   * <p>
   * key值为：consumerId@IP:port  <br>
   * value值为: 最后一次调用失败时间，以当时的毫秒时间戳记录   <br>
   * 其中consumerId指的是客户端在zk上注册的URL的字符串形式，@是分隔符，IP:port指的是服务提供者的IP和端口
   * <p/>
   */
  private volatile static Map<String, Long> lastFailingTime = new HashMap<>();

  /**
   * 各个【客户端对应服务提供者】服务调用失败次数
   * <p>
   * key值为：consumerId@IP:port  <br>
   * value值为: 失败次数   <br>
   * 其中consumerId指的是客户端在zk上注册的URL的字符串形式，@是分隔符，IP:port指的是服务提供者的IP和端口
   * <p/>
   */
  private volatile static ConcurrentHashMap<String, AtomicInteger> requestFailures = new ConcurrentHashMap<>();


  /**
   * 记录失败次数
   *
   * @author sxp
   * @since 2018-6-21
   */
  public static <ReqT, RespT> void recordFailure(ClientCall<ReqT, RespT> call, Channel channel, String method) {
    if (channel == null) {
      return;
    }

    NameResolver nameResolver = channel.getNameResolver();
    if (nameResolver == null) {
      return;
    }

    String consumerId = nameResolver.getSubscribeId();
    if (StringUtils.isEmpty(consumerId)) {
      return;
    }

    String providerId = FailoverUtils.getProviderId(channel);
    if (StringUtils.isEmpty(providerId)) {
      return;
    }

    Object argument = FailoverUtils.getArgument(nameResolver);

    String key = consumerId + CONSUMERID_PROVIDERID_SEPARATOR + providerId;

    long currentTimestamp = System.currentTimeMillis();
    long lastTimestamp;  // 最后一次服务调用失败时间

    if (lastFailingTime.containsKey(key)) {
      lastTimestamp = lastFailingTime.get(key);
    } else {
      lastTimestamp = currentTimestamp;
    }
    lastFailingTime.put(key, currentTimestamp);

    // 更新客户端对应服务提供者列表
    updateFailingProviders(consumerId, providerId);

    // 更新失败次数
    updateFailTimes(nameResolver, consumerId, providerId, lastTimestamp, currentTimestamp, argument, method);
  }

  /**
   * 更新客户端对应服务提供者列表
   *
   * @author sxp
   * @since 2018-6-25
   */
  private static void updateFailingProviders(String consumerId, String providerId) {
    ConcurrentHashSet<String> providers;

    if (failingProviders.containsKey(consumerId)) {
      providers = failingProviders.get(consumerId);
    } else {
      providers = new ConcurrentHashSet<>();
      ConcurrentHashSet<String> oldValue = failingProviders.putIfAbsent(consumerId, providers);
      if (oldValue != null) {
        providers = oldValue;
      }
    }

    if (!providers.contains(providerId)) {
      try {
        providers.add(providerId);
      } catch (Exception e) {
        logger.info("更新客户端对应服务提供者列表出错", e);
      }
    }
  }

  /**
   * 服务调用失败次数
   *
   * @author sxp
   * @since 2018-6-25
   */
  private static void updateFailTimes(NameResolver nameResolver, String consumerId, String providerId,
                                      long lastTimestamp, long currentTimestamp, Object argument, String method) {
    AtomicInteger failTimes;// 失败次数

    String key = consumerId + CONSUMERID_PROVIDERID_SEPARATOR + providerId;

    if (!requestFailures.containsKey(key)) {
      failTimes = new AtomicInteger(0);
      AtomicInteger oldValue = requestFailures.putIfAbsent(key, failTimes);
      if (oldValue != null) {
        failTimes = oldValue;
      }
    } else {
      failTimes = requestFailures.get(key);

      // 为了提高性能，不对调用成功的情况进行记录，使用以下策略近似判断连续多次调用失败：
      // 将当前时间和最后一次出错时间的记录做比较，如果时间间隔大于10分钟，将之前的错误次数清0
      if (currentTimestamp - lastTimestamp > DateUtils.TEN_MINUTES_IN_MILLIS) {
        failTimes.set(0);
      }
    }

    failTimes.incrementAndGet();

    int consumerProvidersAmount = getConsumerProvidersAmount(nameResolver);// 客户端服务列表中服务提供者的数量
    boolean isZkProviderListEmpty = isZkProviderListEmpty(nameResolver);// 注册中心上服务提供者列表是否为空

    if (failTimes.get() >= switchoverThreshold) {
      if (consumerProvidersAmount == 1) {
        if (punishTime == 0) {
          // 什么也不做 ---- 如果服务提供者不恢复正常，之后的调用会一直报错
        } else {
          // 记录最后一个服务提供者的删除时间key=consumerId
          removeProviderTimestamp.put(consumerId, currentTimestamp);

          // 将当前出错的服务提供者从备选列表中剔除
          removeCurrentProvider(nameResolver, providerId, method);
        }
      } else if (consumerProvidersAmount > 1){// 存在多个服务提供者的情况
        removeCurrentProvider(nameResolver, providerId, method);
      }

      consumerProvidersAmount = getConsumerProvidersAmount(nameResolver);// 重新获取(可能调用removeCurrentProvider)

      if (consumerProvidersAmount > 0) {
        // 重选服务提供者
        try {
          logger.info("重选服务提供者");
          nameResolver.resolveServerInfo(argument, method);
        } catch (Throwable t) {
          logger.info("重选服务提供者出错", t);
        }
      }

      failTimes.set(0);// 重置请求出错次数
    }

    consumerProvidersAmount = getConsumerProvidersAmount(nameResolver);// 重新获取(可能调用resolveServerInfo)

    if (consumerProvidersAmount == 0 && !isZkProviderListEmpty && punishTime > 0) {
      long removeTime;
      if (removeProviderTimestamp.containsKey(consumerId)) {
        removeTime = removeProviderTimestamp.get(consumerId);
      } else {
        removeTime = 0L;
      }

      if (currentTimestamp - removeTime >= punishTimetoMillis) {
        // 重新查询一遍服务提供者，将注册中心上的服务列表写入当前消费者的服务列表
        if (nameResolver instanceof ZookeeperNameResolver) {
          ZookeeperNameResolver zkResolver = (ZookeeperNameResolver) nameResolver;

          String serviceName = zkResolver.getServiceName();
          Object lock = zkResolver.getLock();

          synchronized (lock) {// 这里相当于模拟服务列表发生变化，需要加锁
            zkResolver.getAllByName(serviceName);

            try {
              logger.info("重选服务提供者");
              nameResolver.resolveServerInfo(argument, method);
            } catch (Throwable t) {
              logger.info("重选服务提供者出错", t);
            }
          }
        }

        // 然后将removeProviderTimestamp中的这个消费者的数据删除
        removeProviderTimestamp.remove(consumerId);
      } else {
        String serviceName = nameResolver.getServiceName();
        logger.info("注册中心上存在{}服务提供者，但是客户端调用多次出错，在惩罚时间{}秒内服务提供者不可用", serviceName, punishTime);
      }
    }
  }

  /**
   * 将当前出错的服务器从备选列表中去除
   *
   * @author sxp
   * @since 2018-6-21
   */
  private static void removeCurrentProvider(NameResolver nameResolver, String providerId, String method) {
    Map<String, ServiceProvider> providersForLoadBalance = nameResolver.getProvidersForLoadBalance();
    if (providersForLoadBalance == null || providersForLoadBalance.size() == 0) {
      return;
    }

    if (providersForLoadBalance.containsKey(providerId)) {
      logger.info("将当前出错的服务器{}从备选列表中删除", providerId);
      providersForLoadBalance.remove(providerId);
      nameResolver.reCalculateProvidersCountAfterLoadBalance(method);
    }
  }

  /**
   * 获取客户端服务列表中服务提供者的数量
   *
   * @author sxp
   * @since 2018-7-7
   */
  private static int getConsumerProvidersAmount(NameResolver nameResolver) {
    Map<String, ServiceProvider> providersForLoadBalance = nameResolver.getProvidersForLoadBalance();
    if (providersForLoadBalance == null) {
      return 0;
    }
    return providersForLoadBalance.size();
  }

  /**
   * 注册中心上该消费者的服务提供者列表是否为空
   *
   * @author sxp
   * @since 2018-7-7
   */
  private static boolean isZkProviderListEmpty(NameResolver nameResolver) {
    ProvidersListener listener = nameResolver.getProvidersListener();
    if (listener == null) {
      return true;// 为空
    }

    return listener.isProviderListEmpty();
  }


  /**
   * 删除与当前客户端相关的数据(服务调用出错次数、时间、当前客户端对应的服务提供者列表)
   *
   * @author sxp
   * @since 2018-6-25
   */
  static void removeDateByConsumerId(String consumerId) {
    if (!failingProviders.containsKey(consumerId)) {
      return;// consumerId对应的客户端没有出现过调用出错的情况
    }

    ConcurrentHashSet<String> providerIds = failingProviders.get(consumerId);
    String key;

    for(String providerId : providerIds) {
      key = consumerId + CONSUMERID_PROVIDERID_SEPARATOR + providerId;
      lastFailingTime.remove(key);// 服务最后一次调用出错时间
      requestFailures.remove(key);// 服务调用出错次数
    }

    failingProviders.remove(consumerId);// 服务提供者列表

    removeProviderTimestamp.remove(consumerId);// 最后一个服务提供者的删除时间
  }

}
