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

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HelloWorldServer {
  private static final Logger logger = LoggerFactory.getLogger(HelloWorldServer.class);

  private Server server;

  private void start() throws IOException {
    int port = 50051;
    server = ServerBuilder.forPort(port)
            // 在连接上没有发生服务调用时，是否允许客户端发送保持活动的HTTP/2 ping
            .permitKeepAliveWithoutCalls(true)
            // 允许客户端配置的最积极(最小)的保持活动时间(服务器将尝试检测超过此速率的客户端，并在检测到时强制关闭连接)
            // 这个值要小于或者等于客户端设置的keepAliveTime(keepAliveTime, timeUnit)的值
            .permitKeepAliveTime(10, TimeUnit.MINUTES)
            .addService(new GreeterImpl())
            .build()
            .start();
    logger.info("Server started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        HelloWorldServer.this.stop();
        System.err.println("*** server shut down");
      }
    });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
    @Override
    public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      System.out.println("server gets msg[" + req.getId() + " & " + req.getName() + "]");
      HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void echo(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      System.out.println("server gets msg[" + req.getId() + " & " + req.getName() + "]");
      HelloReply reply = HelloReply.newBuilder().setMessage(req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final HelloWorldServer server = new HelloWorldServer();
    server.start();
    //TimeUnit.SECONDS.sleep(10);
    //server.stop();
    server.blockUntilShutdown();
  }
}
