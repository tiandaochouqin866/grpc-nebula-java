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
/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orientsec.grpc.examples.helloworld;

import com.orientsec.grpc.examples.common.NameCreater;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class HelloWorldClient {
  private static final Logger logger = LoggerFactory.getLogger(HelloWorldClient.class);

  private final ManagedChannel channel;
  private final GreeterGrpc.GreeterBlockingStub blockingStub;

  public HelloWorldClient() {
    //this(ManagedChannelBuilder.forAddress(host, port)
    //    .usePlaintext()
    //    .build());

    String target = "zookeeper:///" + GreeterGrpc.SERVICE_NAME;

    channel = ManagedChannelBuilder.forTarget(target)
            // 在连接上没有发生服务调用时，是否允许发送HTTP/2 ping
            .keepAliveWithoutCalls(true)
            // 每隔多长时间发送一次HTTP/2 ping(最小值为10秒)
            .keepAliveTime(10, TimeUnit.MINUTES)
            // 发送HTTP/2 ping后等待服务端对ping进行响应结果的时间，超过这个时间则认为服务端不可用
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .usePlaintext()
            .build();

    blockingStub = GreeterGrpc.newBlockingStub(channel);
  }


  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * 向服务端发送请求
   */
  public void greet(int id, String name) {
    logger.info("Will try to greet " + name + " ...");
    HelloRequest request = HelloRequest.newBuilder().setId(id).setName(name).build();
    HelloReply response;
    try {
      // blockingStub = GreeterGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);
      if (id % 2 == 0) {
        response = blockingStub.sayHello(request);
      } else {
        response = blockingStub.echo(request);
      }

    } catch (StatusRuntimeException e) {
      if (e.getStatus() != null && Status.Code.DEADLINE_EXCEEDED.equals(e.getStatus().getCode())) {
        logger.error("服务调用超时", e);
      } else {
        logger.error("RPC failed:", e);
      }
      return;
    } catch (Exception e) {
      logger.error("RPC failed:", e);
      return;
    }
    logger.info("Greeting response: " + response.getMessage());
  }


  public static void main(String[] args) throws Exception {
    HelloWorldClient client = new HelloWorldClient();

    try {
      long count = 0;
      long interval = 5000L;// 时间单位为毫秒
      int id;
      long LOOP_NUM = 1000;

      Random random = new Random();
      List<String> names = NameCreater.getNames();
      int size = names.size();

      while (true) {
        if (count++ >= LOOP_NUM) {
          break;
        }

        id = random.nextInt(size);
        client.greet(id, names.get(id));

        TimeUnit.MILLISECONDS.sleep(interval);
      }
    } finally {
      client.shutdown();
    }
  }
}
