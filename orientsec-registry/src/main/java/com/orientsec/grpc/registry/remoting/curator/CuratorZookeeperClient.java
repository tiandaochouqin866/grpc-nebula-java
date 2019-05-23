/*
 * Copyright 2018-2019 The Apache Software Foundation
 * Modifications 2019 Orient Securities Co., Ltd.
 * Modifications 2019 BoCloud Inc.
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


package com.orientsec.grpc.registry.remoting.curator;

import com.orientsec.grpc.common.constant.GlobalConstants;
import com.orientsec.grpc.common.resource.SystemConfig;
import com.orientsec.grpc.common.util.PropertiesUtils;
import com.orientsec.grpc.registry.common.URL;
import com.orientsec.grpc.registry.common.utils.StringUtils;
import com.orientsec.grpc.registry.remoting.ChildListener;
import com.orientsec.grpc.registry.remoting.StateListener;
import com.orientsec.grpc.registry.remoting.support.AbstractZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;


/**
 * Created by heiden on 2017/3/15.
 */
public class CuratorZookeeperClient extends AbstractZookeeperClient<CuratorWatcher> {
  private final static Logger logger = LoggerFactory.getLogger(CuratorZookeeperClient.class);

  private final CuratorFramework client;

  public CuratorZookeeperClient(URL url) {
    super(url);

    // zk断线重连时，每3秒重连一次，直到连接上zk，或者超过最大重连时间才停止
    int maxElapsedTimeMs = getMaxElapsedTimeMs();
    int sleepMsBetweenRetries = 3 * 1000;

    CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
            .connectString(url.getBackupAddress())
            .retryPolicy(new RetryUntilElapsed(maxElapsedTimeMs, sleepMsBetweenRetries))
            .connectionTimeoutMs(getConnectionTimeoutMs())
            .sessionTimeoutMs(getSessionTimeoutMs());

    String userPassword = ZkACLProvider.getUserPassword();
    if (StringUtils.isNotEmpty(userPassword)) {
      byte[] auth = ZkACLProvider.getUserPassword().getBytes(StandardCharsets.UTF_8);
      String scheme = ZkACLProvider.getScheme();
      builder = builder.aclProvider(new ZkACLProvider()).authorization(scheme, auth);
    }

    client = builder.build();
    client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
      public void stateChanged(CuratorFramework client, ConnectionState state) {
        if (state == ConnectionState.LOST) {
          CuratorZookeeperClient.this.stateChanged(StateListener.DISCONNECTED);
        } else if (state == ConnectionState.CONNECTED) {
          CuratorZookeeperClient.this.stateChanged(StateListener.CONNECTED);
        } else if (state == ConnectionState.RECONNECTED) {
          CuratorZookeeperClient.this.stateChanged(StateListener.RECONNECTED);
        }
      }
    });
    client.start();
  }

  private static int getMaxElapsedTimeMs() {
    String key = GlobalConstants.REGISTRY_RETRY_TIME;
    // 缺省值为1天(86400*1000)
    int defaultValue = 86400000;
    Properties properties = SystemConfig.getProperties();

    int value = PropertiesUtils.getValidIntegerValue(properties, key, defaultValue);
    if (value <= 0 || value < 100) {
      value = defaultValue;
    }

    return value;
  }

  private static int getConnectionTimeoutMs() {
    String key = GlobalConstants.REGISTRY_CONNECTIONTIMEOUT;
    int defaultValue = 5000;
    Properties properties = SystemConfig.getProperties();

    int value = PropertiesUtils.getValidIntegerValue(properties, key, defaultValue);
    if (value <= 0 || value < 100) {
      value = defaultValue;
    }

    return value;
  }

  private static int getSessionTimeoutMs() {
    String key = GlobalConstants.REGISTRY_SESSIONTIMEOUT;
    int defaultValue = 4000;
    Properties properties = SystemConfig.getProperties();

    int value = PropertiesUtils.getValidIntegerValue(properties, key, defaultValue);
    if (value <= 0 || value < 100) {
      value = defaultValue;
    }

    return value;
  }

  public void createPersistent(String path) {
    try {
      client.create().forPath(path);
    } catch (KeeperException.NodeExistsException e) {
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public void createEphemeral(String path) {
    try {
      client.create().withMode(CreateMode.EPHEMERAL).forPath(path);
    } catch (KeeperException.NodeExistsException e) {
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public void delete(String path) {
    try {
      client.delete().forPath(path);
    } catch (KeeperException.NoNodeException e) {
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public List<String> getChildren(String path) {
    try {
      return client.getChildren().forPath(path);
    } catch (KeeperException.NoNodeException e) {
      return null;
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public String doGetData(String path){
    try {
      return new String(client.getData().forPath(path));
    } catch (KeeperException.NoNodeException e) {
      return null;
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public boolean isConnected() {
    return client.getZookeeperClient().isConnected();
  }

  public void doClose() {
    client.close();
  }

  private class CuratorWatcherImpl implements CuratorWatcher {

    private volatile ChildListener listener;

    public CuratorWatcherImpl(ChildListener listener) {
      this.listener = listener;
    }

    public void unwatch() {
      this.listener = null;
    }

    /**
     * @since 2019-3-1 modify by sxp 忽略zookeeper的WatchedEvent中path为空的情况
     */
    @Override
    public void process(WatchedEvent event) throws Exception {
      String path = event.getPath();
      if (StringUtils.isEmpty(path)) {
        logger.info("Ignore this event(" + event + ").");
        return;
      }

      if (listener != null) {
        listener.childChanged(path, client.getChildren().usingWatcher(this).forPath(event.getPath()));
      }
    }
  }

  public CuratorWatcher createTargetChildListener(String path, ChildListener listener) {
    return new CuratorWatcherImpl(listener);
  }

  public List<String> addTargetChildListener(String path, CuratorWatcher listener) {
    try {
      return client.getChildren().usingWatcher(listener).forPath(path);
    } catch (KeeperException.NoNodeException e) {
      return null;
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public void removeTargetChildListener(String path, CuratorWatcher listener) {
    ((CuratorWatcherImpl) listener).unwatch();
  }

}
