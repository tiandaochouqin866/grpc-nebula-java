# <center>微服务治理框架(Java版)详细设计</center>

---

## 一、服务端

### 1. 服务启动时，注册服务信息
- 原理分析

服务启动调用的是 `io.grpc.internal.ServerImpl#start` 方法，在该方法的最后调用注册服务信息的接口。

- 实现思路

为了方便后续的升级，新增 `orientsec-grpc-provider` 模块，在这个模块中定义服务注册的接口和实现方法。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ServerImpl#start 
	
	orientsec-grpc-provider 模块：
	新增服务注册接口 com.orientsec.grpc.provider.core.ProviderServiceRegistry#register 
	新增服务注册实现 com.orientsec.grpc.provider.core.ProviderServiceRegistryImpl#register

### 2. 服务关闭时，注销服务信息
- 原理分析

服务关闭调用的是 `io.grpc.internal.ServerImpl#shutdown` 方法，在该方法的最后调用注销服务信息的接口。

- 实现思路

在新增的 `orientsec-grpc-provider` 模块中实现注销服务信息。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ServerImpl#shutdown
	
	orientsec-grpc-provider 模块：
	新增注销服务接口 com.orientsec.grpc.provider.core.ProviderServiceRegistry#unRegister
	新增注销服务实现 com.orientsec.grpc.provider.core.ProviderServiceRegistryImpl#unRegister

### 3. 服务流量控制
- 原理分析

服务端可以采取两种手段进行服务流量控制，一种是并发请求数控制，另一种是并发连接数控制。

并发请求数，指的是服务端同一时刻最多可以处理的请求数量。

并发连接数，指的是与服务端建立的 HTTP/2 连接数量，一个连接对应一个客户端。

服务端收到客户端请求时会调用 `io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#halfClosed` 方法，处理完客户端请求后会调用 `io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#closed` 方法，因此可以通过修改这两个方法进行并发请求数的控制。

服务端启动时会调用 `io.grpc.netty.NettyServer#start` 方法 ，该方法定义了服务端与客户端建立连接的初始化方法，可以通过修改这段代码来实现连接数控制。
    
- 实现思路

a. 并发请求数流量控制：

（1）为服务端增加一个计数器，用来记录并发请求数。

（2）每接收到一个请求时，检查当前的请求数是否达到最大请求数。如果已经达到最大请求数直接拒绝客户端的请求（客户端会接收到一个异常信息），如果未达到计数器增加1；

（3）每处理完一个请求时，计数器减少1。

（4）注意事项：实现计数器时要进行并发控制，例如使用JDK提供的线程安全的数据结构 AtomicInteger 。


b. 连接数控制流量控制：

服务端有一个集合类记录着服务端与客户端建立的连接信息。当接收到客户端建立连接的请求时，检查当前的连接数是否达到最大的连接数。如果已经达到最大连接数，拒绝客户端建立连接的请求（客户端会接收到一个异常信息）；如果未达到，建立连接，并将连接信息记录到集合类中。

- 场景描述

并发请求数使用default.requests参数进行配置，连接数使用default.connections参数进行配置。

并发请求数参数和连接数参数可以同时配置，这是从两个不同的维度进行流量控制。前者控制的是服务者的并发处理能力，后者控制的是与之连接的客户端的个数。

默认情况下，并发请求数参数值为2000，连接数为20。即同一时刻最多可以有20个客户端与服务端建立连接，同时服务端处理客户端的请求最大并发数为2000。

如果因为客户端超过了默认连接数20的限制，从而导致客户端出现异常，可以通过服务治理平台配置该服务的最大连接数。

如果因为客户端调用频繁、并发程度高，或者服务端处理业务逻辑时间较长，可以通过服务治理平台配置该服务的最大并发请求数。


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#halfClosed
	修改 io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#closed
	修改 io.grpc.internal.ServerImpl.ServerListenerImpl#getServerTransportCount
	修改 io.grpc.netty.NettyServer#start
	
	orientsec-grpc-provider 模块：
	新增 com.orientsec.grpc.provider.qos.ProviderRequestsControllerUtils
	新增 com.orientsec.grpc.provider.qos.RequestsController


### 4. 服务端配置信息监听
- 实现思路

在服务注册的最后一步，注册一个监听当前服务的监听器，用来监听注册中心上该服务的以下几种配置信息。

（1）服务并发请求数配置(default.requests)

当监听到注册中心上并发请求数参数配置发现改变后，将新的参数值更新到内存中；当监听到注册中心上并发请求参数配置信息被删除后，查询服务端初始的并发请求数配置，将内存中的并发请求数恢复到初始配置。

（2）服务连接数配置(default.connections)

当监听到注册中心上连接数参数配置发现改变后，将新的参数值更新到内存中；当监听到注册中心上连接数参数配置信息被删除后，查询服务端初始的连接数配置，将内存中的连接数恢复到初始配置。

（3）访问保护状态配置(access.protected)

当监听到注册中心上访问保护状态配置发现改变后，将新的参数值更新到内存中。当监听到注册中心上访问保护状态配置信息被删除后，查询服务端初始的访问保护状态，将内存中的访问保护状态恢复到初始配置。

然后根据当前的访问保护状态的参数值进行以下操作：

如果参数值为true，向注册中心写入一条“禁止所有客户端访问当前服务端的路由规则”；

如果参数值为false，将注册中心上“禁止所有客户端访问当前服务端的路由规则”删除。

（4）服务是否有新版本配置(deprecated)

当监听到注册中心上deprecated参数配置发现改变后，将新的参数值更新到内存中；当监听到注册中心上deprecated参数配置信息被删除后，查询服务端初始的deprecated配置，将内存中的deprecated参数值恢复到初始配置。


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-provider 模块：
	新增 com.orientsec.grpc.provider.watch.ProvidersListener
	新增 com.orientsec.grpc.provider.watch.RequestsHandler
	新增 com.orientsec.grpc.provider.watch.ConnectionsHandler
	新增 com.orientsec.grpc.provider.watch.DeprecatedHandler
	新增 com.orientsec.grpc.provider.watch.AccessProtectedHandler

### 5. 服务过时打印告警日志
- 原理分析

服务端收到客户端请求时会调用 `io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#halfClosed` 方法，可以通过修改这个方法来实现。

- 实现思路

当服务端被调用时，如果当前服务的deprecated参数值为true，打印告警日志，1天只打印一次告警日志。

1天只打印一次日志可以通过增加一个变量存储“上一次记录deprecated日志的时间戳”来实现。

告警日志内容示例：当前服务[com.orientsec.grpc.examples.helloworld.Greeter]已经过时，请检查是否新服务上线替代了该服务。


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ServerCallImpl.ServerStreamListenerImpl#halfClosed
	
	orientsec-grpc-provider 模块：
	新增 com.orientsec.grpc.provider.core.ServiceConfigUtils#getAllServicesConfig


## 二、客户端
### 6. 基于zookeeper的NameResolver
- 原理分析

原生 grpc 有两种方式确定服务端：

（1）直接指定主机名或IP地址、端口

（2）指定目标地址为 `"dns:///主机名或IP地址:端口号"` 

增加第三种方式来确定服务端：

（3）指定目标地址为 `"zookeeper:///包含完整路径的服务名称" `

最后这种方式需要增加一个基于zookeeper的注册中心，根据这个字符串从注册中心上查找出服务端信息，然后根据一定的策略动态选择其中一台服务端。

- 实现思路

参考 `io.grpc.internal.DnsNameResolver` `io.grpc.internal.DnsNameResolverProvider` 新增一个使用 zookeeper作为注册中心解析目标服务端的类 。

解析出服务名称之后，将注册中心上该服务的服务端地址缓存到内存中，同时对该服务端地址进行监听。在客户端发起请求时，根据客户端的负载均衡策略，选择一个服务端地址。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：		
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver        
	新增com.orientsec.grpc.consumer.internal.ZookeeperNameResolverProvider
	修改配置文件 resources\META-INF\services\io.grpc.NameResolverProviders

### 7. 客户端启动时，注册客户端信息
- 原理分析

客户端启动时，会调用 `io.grpc.internal.ManagedChannelImpl#ManagedChannelImpl` 创建 `ManagedChannel` 对象。在创建 `ManagedChannel` 对象时，增加调用注册客户端信息的接口。

- 实现思路

新增 `orientsec-grpc-consumer` 模块，在该模块中增加注册客户端信息的接口和实现。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ManagedChannelImpl#ManagedChannelImpl
	新增 io.grpc.NameResolver#registry		
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#registry
	
	orientsec-grpc-consumer 模块： 
	新增 com.orientsec.grpc.consumer.core.ConsumerServiceRegistry#register
	新增 com.orientsec.grpc.consumer.core.DefaultConsumerServiceRegistryImpl#register


### 8. 客户端关闭时，注销客户端信息
- 原理分析

客户端关闭时会调用 `io.grpc.internal.ManagedChannelImpl#shutdown` ，这个方法会调用 `com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#shutdown` ，在这个方法里可以调用注销客户端信息的接口。

- 实现思路

在 `orientsec-grpc-consumer` 模块中，增加注册客户端信息的接口方法。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#shutdown
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#unRegistry
	
	orientsec-grpc-consumer 模块：
	新增 com.orientsec.grpc.consumer.core.ConsumerServiceRegistry#unSubscribe
	新增 com.orientsec.grpc.consumer.core.DefaultConsumerServiceRegistryImpl#unSubscribe


### 9. 监听服务端信息
- 原理分析

在客户端启动，注册客户端信息时，同时注册一个监听器，用来监听服务端列表的变化。同时，将服务端列表存储到内存中。

当监听到服务端上线时，更新缓存。当监听到有服务端下线时，更新缓存。

- 实现思路

在 `orientsec-grpc-consumer` 模块中，修改客户端注册的方法，在注册客户端信息的同时，注册一个服务端列表的监听器。然后在监听器中实现服务端列表更新后的业务逻辑。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-consumer 模块：
	修改 com.orientsec.grpc.consumer.task.RegistryTask#doRegister
	新增 com.orientsec.grpc.consumer.internal.ProvidersListener


### 10. 实现客户端对路由规则的解析方法，用来过滤服务端列表
- 原理分析

![路由规则判断流程图](https://raw.githubusercontent.com/grpc-nebula/grpc-nebula/master/images/routing-rules-judge-flow-diagram.png)

- 实现思路

在 `orientsec-grpc-core` 模块中，首先注册一个监听路由规则的监听器 `RoutersListener` ，然后新增一个路由规则解析器 `ConditionRouter` ，然后在获取服务端列表的模块 `ZookeeperNameResolver` 调用路由规则解析器，实现服务端列表的过滤。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#applyRoute
	新增 com.orientsec.grpc.consumer.routers.ConditionRouter
	新增 com.orientsec.grpc.consumer.internal.RoutersListener

### 11. 支持路由规则可以设置为IP段、项目
- 原理分析

路由规则由两个条件组成，分别用于对客户端和服务端进行匹配。比如有这样一条规则：

	host = 192.168.1.* => host = 192.168.2.*

该条规则表示 IP 为 192.168.1.* 的客户端只可调用 IP 为 192.168.2.* 服务器上的服务，不可调用其他服务端上的服务。路由规则的格式如下：

[客户端匹配条件] => [服务端匹配条件]

如果客户端匹配条件为空，表示不对客户端进行限制。如果服务端匹配条件为空，表示对某些客户端禁用服务。

客户端匹配条件不仅可以限定到IP(或IP段)，也可以限定到项目，例如这样一条规则：

	project = grpc-test-apps => host = 192.168.2.*

表示项目名称为 grpc-test-apps 的客户端只可调用 IP 为 192.168.2.* 服务器上的服务。

**需要特别注意的是**：IP段条件（例如192.168.2.*）中只能有一个星号(\*)。星号既可以在开头，也可以在末尾，还可以在中间。

- 实现思路

判断一个IP地址是否在IP段中，可以按照如下逻辑来进行：

（1）当星号在末尾，截取星号之前的字符串（记为 prefix ），如果IP地址是以 prefix 开头的，则判定为匹配；

（2）当星号在开头，截取星号之后的字符串（记为 suffix ），如果IP地址是以 suffix 结尾的，则判定为匹配；

（3）当星号在中间，截取星号之前的字符串（记为 prefix ）和星号之后的字符串（记为 suffix ），如果IP地址是以 prefix 开头，同时以 suffix 结尾，则判定为匹配。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：		
	修改 com.orientsec.grpc.consumer.routers.ConditionRouter
	
	orientsec-grpc-registry 模块：
	新增 com.orientsec.grpc.registry.common.utils.UrlUtils#isMatchGlobPattern


### 12. 监听路由规则，获取可访问的服务列表
- 原理分析

在客户端启动注册客户端信息时，同时注册一个监听器，用来监听路由规则。路由规则在内存中缓存一份。

当监听到路由规则发生变化时，更新内存中缓存的路由规则。路由规则发生变化后，还需要重新计算当前客户端的服务端列表，然后将最新的服务端列表缓存更新到内存中。

- 实现思路

首先，在模块注册客户端信息时，增加注册路由规则的监听器。然后实现监听到路由规则发生变化后，对内存中路由规则、客户端可能访问的服务端列表进行更新。

每一个服务客户端，对应着一个服务名称，每一个客户端需要监听的路由规则是隶属于该服务名称的路由规则。路由规则在内存中以列表的方式存储：

    List<Router> routes = new ArrayList<>();

路由规则在程序中对应的对象是一个自定义的URL。而这里的Router是一个接口，接口的定义如下：

    public interface Router extends Comparable<Router> {
      /**
       * 获取路由规则的URL表示形式
       */
      URL getUrl();
    
      /**
       * 应用路由规则
       *
       * @param providers 客户端对应的所有服务端列表
       * @param url 客户端对应的URL
       * @return 应用路由规则后符合条件的服务端列表
       */
       Map<String,ServiceProvider> route(Map<String, ServiceProvider> providers, URL url) throws RpcException;
    }

下面是URL的定义：

    public final class URL implements Serializable {
      private static final long serialVersionUID = -1985165475234910535L;
    
      private final String protocol;
      private final String username;
      private final String password;
      private final String host;
      private final int port;
      private final String path;
      private final Map<String, String> parameters;
    
      private transient volatile  Map<String, Number> numbers;
      private transient volatile  Map<String, URL> urls;
      private transient volatile  String ip;
      private transient volatile  String full;
      private transient volatile  String identity;
      private transient volatile  String parameter;
      private transient volatile  String string;
    
      protected URL() {
        this.protocol = null;
        this.username = null;
        this.password = null;
        this.host = null;
        this.port = 0;
        this.path = null;
        this.parameters = null;
      }
      
      ...
    }


- 相关代码

涉及到的模块与代码：

    orientsec-grpc-consumer 模块：
    新增 com.orientsec.grpc.consumer.routers.Router
    修改 com.orientsec.grpc.consumer.task.RegistryTask#doRegister
    
    orientsec-grpc-registry 模块：
    新增 com.orientsec.grpc.registry.common.URL
    
    orientsec-grpc-core 模块：
    新增：com.orientsec.grpc.consumer.internal.RoutersListener		

### 13. 监听服务端权重信息
- 原理分析

服务端权重表示的是服务端提供服务的能力，也可以简单地理解为服务端所在服务器的配置。服务端的权重越高，表示服务器提供服务的能力越强。

如果配置的负载均衡算法为“加权轮询算法”，那么各服务端被调用次数的比例等于服务端权重的比例。

- 实现思路

提供同一个服务的服务端可能有多个，因此一个客户端内存中存储的服务端权重信息在一个集合数据类型中。

当监听到注册中心上服务端权重参数配置值发生改变，客户端将内存中的服务权重更新为修改后的参数值；当监听到注册中心上服务端权重配置信息被删除后，查询服务端初始的权重，将内存中的服务权重恢复到初始配置。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ConfiguratorsListener
	新增 com.orientsec.grpc.consumer.internal.ProviderWeightHandler

### 14. 监听服务端是否过时
- 原理分析

缺省情况下，服务端过时标志参数值为false。如果客户端就检测到服务端的过时标志被设置为true，那么客户端调用该服务端时会记录一条警告类型的日志信息。

- 实现思路

当监听到注册中心上服务端过时标志参数配置值发生改变，客户端将内存中的服务端过时标志更新为修改后的参数值；当监听到注册中心上服务端过时标志配置信息被删除后，查询服务端初始的过时标志，将内存中的服务端过时标志恢复到初始配置。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ConfiguratorsListener
	新增 com.orientsec.grpc.consumer.internal.ProviderDeprecatedHandler


### 15. 客户端监听配置信息的更新
- 原理分析

客户端需要监听的配置信息有以下几种：

（1）负载均衡模式 loadbalance.mode

负载均衡模式用来控制客户端每次请求服务端时，是否调用负载均衡算法选择一个新的服务端。

（2）负载均衡算法 default.loadbalance

负载均衡算法用来从服务端列表中选择一个服务端时采用哪种算法。

（3）客户端指定的服务端版本号 service.version

客户端指定了服务端版本号之后，程序会优先选择具有指定版本的服务端；如果注册中心没有该版本的服务端，则不限制版本重新选择服务提供者。(使用场景：灰度发布、A/B测试)

（4）客户端对服务端的每秒钟的请求次数参数 consumer.default.requests

用来限制客户端对服务端的请求频率。


- 实现思路

当监听到负载均衡模式参数值发生变化后，将新的负载均衡模式更新到内存中，在客户端再次向服务端发起请求时，客户端就可以通过查看内存中的负载均衡模式来确定是否需要重新更换服务端；当监听到负载均衡模式配置信息被删除后，将负载均衡模式恢复为默认值（默认值为：连接负载均衡模式）。

当监听到负载均衡算法参数值发生变化后，将新的负载均衡算法更新到内存中；当监听到负载均衡算法配置信息被删除后，将负载均衡算法恢复为默认值（默认值为：轮询算法）。

当监听到服务端版本号参数值发生变化后，将新的服务端版本号更新到内存中，同时根据服务端版本号重选服务端；当监听到服务端版本号配置信息被删除后，将服务端版本号恢复为默认值（默认值为：空字符串），同时根据服务端版本号重选服务端。

当监听到客户端对服务端的每秒钟的请求次数参数值发生变化后，将新的每秒钟的请求次数参数更新到内存中；当监听到每秒钟的请求次数参数配置信息被删除后，将客户端对服务端的每秒钟的请求次数参数恢复为默认值（默认值为：0,即不限制）。


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ConfiguratorsListener
	新增 com.orientsec.grpc.consumer.internal.LoadbalanceModeHandler
	新增 com.orientsec.grpc.consumer.internal.LoadbalanceHandler
	新增 com.orientsec.grpc.consumer.internal.ServiceVersionHandler
	新增 com.orientsec.grpc.consumer.internal.ConsumerRequestsHandler



### 16. 限制客户端对某个服务每秒钟的请求次数（Requests Per Second）
- 原理分析

增加对客户端请求数控制的功能，限制客户端对某个服务每秒钟的请求次数（Requests Per Second）。

对客户端增加请求数控制的功能，通过在注册中心上动态增加参数配置。（不支持通过在本地文件配置，因为一个系统可能需要调用多个服务端）

为了和服务端区分，将参数的名称定为consumer.default.requests。程序中新增对注册中心上的consumer.default.requests参数进行监听，缺省情况下，不限制客户端对某个服务的请求次数。

- 实现思路

客户端每次发起调用时总会调用 `ClientCalls#startCall` 方法，因此可以在这个方法中增加客户端每秒请求数的控制。

为了能够获取搭配客户端本次调用的方法属于哪个服务端，需要在抽象类 `ClientCall` 增加一个 `getFullMethod` 的方法，根据这个完成方法名，可以提取出服务端的服务名称。既然在抽象类中增加了方法，需要将继承该抽象类的子类也增加上 `getFullMethod` 的实现。

客户端每秒钟的请求次数的控制策略如下：

a. 首先判断当前客户端是否配置了**每秒钟请求次数参数值**，如果没有配置，直接退出流量控制代码段

b. 如果客户端配置了参数，取出**每秒钟请求次数参数值**，如果参数值小于或者等于0，直接退出流量控制代码段

c. 取出调用当前服务的客户端在一秒钟内的调用次数，判断调用次数是否达到**每秒钟请求次数参数值**，如果达到则拦截本次调用，给客户端返回一个异常信息；如果未达到**每秒钟请求次数参数值**，将客户端在一秒钟内的调用次数的计算增加1。这里的调用次数计数器使用的是带有并发控制的 `AtomicLong` 数据结构。


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-stub 模块：
	新增 io.grpc.stub.ClientCalls#startCall
	
	orientsec-grpc-consumer 模块：
	新增 com.orientsec.grpc.consumer.qos.ConsumerRequestsControllerUtils
	
	orientsec-grpc-core 模块：
	新增 io.grpc.ClientCall#getFullMethod
	新增 io.grpc.internal.ClientCallImpl#getFullMethod
	新增 io.grpc.PartialForwardingClientCall#getFullMethod

### 17. 两种负载均衡模式
- 原理分析

支持两种模式：一种是“请求负载均衡”，另一种是“连接负载均衡”。

“请求负载均衡”指的是每次调用服务端都调用负载均衡算法选择一个服务端。“连接负载均衡”指的是，创建通道(Channel)后第一次调用选择服务端之后，一直复用与之前已选定的服务端建立的连接。

默认情况下为“连接负载均衡”。

- 实现思路

客户端每次调用服务端时，都会调用 ChannelTransportProvider#get 方法向服务端发送数据，在这段代码中判断当前客户端的负载均衡模式。如果负载均衡模式为“请求负载均衡”，先调用负载均衡算法重新选择服务端，然后再继续原来的流程。

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ManagedChannelImpl.ChannelTransportProvider#get


### 18. 四种负载均衡算法
- 原理分析

框架支持以下四种负载均衡算法：

(1) 随机算法 pick_first

实现原理：数据集合下标随机数

(2) 轮询算法 round_robin

实现原理：数据集合下标加1，取余运算

(3) 加权轮询算法 weight_round_robin

实现原理：采用nginx的平滑加权轮询算法。（[算法合理性与平滑性的证明](https://tenfy.cn/2018/11/12/smooth-weighted-round-robin/)）

(4) 一致性Hash算法 consistent_hash

实现原理：采用MD5算法来将对应的key哈希到一个具有2^32次方个桶的空间中，即0~(2^32)-1的数字空间中。同时，引入虚拟机器节点，解决数据分配不均衡的问题。（参考资料[**1**](https://blog.csdn.net/cywosp/article/details/23397179/) [**2**](http://www.cnblogs.com/hapjin/p/4737207.html)）

- 实现思路

(1) 随机算法

先根据服务端的IP:port进行排序，将服务端的个数记为 `N` ，获取一个取值范围在 `0` 至 `N-1` 之间的随机数，将随机数作为服务端列表数据集合的下标，即可得到选中的服务端。

(2) 轮询算法

先根据服务端的IP:port进行排序，将服务端的个数记为 `N`，将上一次选中的服务端对应的数据集合下标记为 `index` , 那么本次选择服务端的下标为` (index + 1) % N` 。

(备注：第一次选择服务端的下标采用的是随机算法)

(3) 加权轮询算法

每个服务器都有两个权重变量：

a：`weight`，配置文件中指定的该服务器的权重，这个值是固定不变的；

b：`current_weight`，服务器目前的权重。一开始为0，之后会动态调整。

选取服务器时遍历所有服务器。对于每个服务器，让它的 `current_weight` 增加它的 `weight`；同时累加所有服务器的 `weight` 保存为 `total` 。

遍历完所有服务器之后，如果该服务器的 `current_weight` 是最大的，就选择这个服务器处理本次请求。最后把该服务器的 `current_weight` 减去 `total` 。


代码片段：

	List<Server> servers = new ArrayList<>();

  	public Server getBestServer() {
    	Server server;
    	Server best = null;
    	int total = 0;
    	int size = servers.size();

    	for (int i = 0; i < size; i++) {
      		server = servers.get(i);
    
      		server.setCurrentWeight(server.getCurrentWeight() + server.getWeight());
      		total += server.getWeight();
    
      		if (best == null || server.getCurrentWeight() > best.getCurrentWeight()) {
        		best = server;
      		}
    	}
    
    	if (best == null) {
      		return null;
    	}
    
    	best.setCurrentWeight(best.getCurrentWeight() - total);
    	return best;
  	}


(4) 一致性Hash算法

算法效果：相同参数的请求总是发到同一服务端。

Hash函数使用md5算法，然后将得到的字节数组通过位运算映射到 `0 ~ 2^32-1` 的环形数字空间中。

为了增加算法的平衡性，虚拟节点的个数的值设置为160。将 `ip:port#i` `(i=0~159)` 作为key值映射到一个 `0 ~ 2^32-1` 的环形数字空间中。

当客户端发起请求时，将客户端的参数值作为key值计算出映射值，然后数据映射在两台虚拟机器所在环之间，按顺时针方向寻找机器。

示意图：

![](https://img-blog.csdn.net/20170108001326379?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvbGloYW8yMQ==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)


- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#generateProvidersForLB
	新增 com.orientsec.grpc.consumer.internal.ZookeeperNameResolver#loadBalancer
	新增 com.orientsec.grpc.consumer.internal.LoadBalancerFactory
	新增 com.orientsec.grpc.consumer.lb.PickFirstLoadBalancer
	新增 com.orientsec.grpc.consumer.lb.RoundRobinLoadBalancer
	新增 com.orientsec.grpc.consumer.lb.WeightRoundRobinLoadBalancer
	新增 com.orientsec.grpc.consumer.lb.ConsistentHashLoadBalancer


### 19. 服务容错
- 原理分析

配置服务调用出错后自动重试次数后，可以启用服务容错功能，当调用某个服务端出错后，框架自动尝试切换到提供相同服务的服务端再次发起请求。

调用某个服务端，如果连续5次请求出错，自动切换到提供相同服务的新服务端。（5这个数值支持配置）

调用某个服务端，如果连续5次请求出错，如果此时没有其他服务端，增加一个惩罚连接时间(例如60s)。

- 实现思路

定义一个客户端调用服务端出现错误的数据集合：

    /**
    * 各个【客户端对应服务提供者】服务调用失败次数
    * <p>
    * key值为：consumerId@IP:port  <br>
    * value值为: 失败次数   <br>
    * 其中consumerId指的是客户端在zk上注册的URL的字符串形式，@是分隔符，IP:port指的是服务提供者的IP和端口
    * <p/>
    */
    ConcurrentHashMap<String, AtomicInteger> requestFailures = new ConcurrentHashMap<>();

当客户端调用服务端抛出异常时，将错误次数累加到以上的数据集合中。当客户端调用同一个服务端失败达到5次时，进行以下处理：

如果服务端个数大于1，将出错的服务端从客户端内存中的服务端候选列表中移除，然后重新选择一个服务端；

如果服务端个数为1，先记录一下当前的时间，然后出错的服务端从客户端内存中的服务端候选列表中移除。

如果服务端个数为0，但是注册中心上服务端个数大于0，并且当前时间与从内存中删除服务端的时间差大于惩罚时间时，将注册中心上服务端列表更新到客户端内存中，然后调用负载均衡算法重新选择服务端。

- 相关代码

涉及到的模块与代码：
	
	orientsec-grpc-core 模块：
	新增 com.orientsec.grpc.consumer.FailoverUtils
	修改 io.grpc.stub.ClientCalls#blockingUnaryCall(Channel, MethodDescriptor<ReqT,RespT>, CallOptions, ReqT)
	修改 io.grpc.stub.ClientCalls#blockingServerStreamingCall(Channel, MethodDescriptor<ReqT,RespT>, CallOptions, ReqT)    


### 20. grpc断线重连指数退避算法支持参数配置功能
- 原理分析

当grpc连接到服务端发生失败时，通常希望不要立即重试(以避免泛滥的网络流量或大量的服务请求)，而是做某种形式的指数退避算法。

相关参数：

(1)INITIAL_BACKOFF (第一次失败重试前等待的时间)

(2)MAX_BACKOFF (失败重试等待时间上限)

(3)MULTIPLIER (下一次失败重试等待时间乘以的倍数)

(4)JITTER (随机抖动因子)

其中MAX_BACKOFF的值为120，单位秒，参数值目前是直接“硬编码”在框架中的，为了优化系统性能，支持不同的义务系统配置不同的参数值，将该参数的取值修改为可配置的。

- 实现思路

在配置文件“dfzq-grpc-config.properties”增加如下参数，修改程序增加对这些配置参数的读取。在框架调用指数退避算法时，参数值优先使用配置文件中的数值：

    
    # 可选,类型long,缺省值120,单位秒,说明:grpc断线重连指数退避协议"失败重试等待时间上限"参数
    # consumer.backoff.max=120
    

- 相关代码

涉及到的模块与代码：

	orientsec-grpc-core 模块：
	修改 io.grpc.internal.ExponentialBackoffPolicy
	新增 com.orientsec.grpc.common.util.PropertiesUtils#getValidDoubleValue


## 三、其它

### 21. 支持Zookeeper开启ACL
- 原理分析

zookeeper支持以下几种权限控制方案：

（1）world: 没有对权限进行限制，这是zookeeper的默认权限控制方案

（2）auth: 通过验证的用户都有权限（zookeeper支持通过kerberos来进行验证, 配置时需要修改zookeeper的配置文件)

（3）digest: 提供username:password用来验证

（4）ip: 需要提供的验证数据为客户机的IP地址，设置的时候可以设置一个ip段，比如ip:192.168.1.0/16, 表示匹配前16个bit的IP段

综合考虑，digest权限控制方案比较适合grpc框架，因此采用这种方案进行访问控制。

- 实现思路

首先，在配置文件中增加zookeeper访问控制用户和密码的配置项。

    # 可选,类型string,访问控制用户名
    # zookeeper.acl.username=admin
    
    # 可选,类型string,访问控制密码
    # 这里的密码配置的是密文，使用com.orientsec.grpc.common.util.DesEncryptUtils#encrypt(String plaintext)进行加密
    # zookeeper.acl.password=9b579c35ca6cc74230f1eed29064d10a

然后，修改创建Zookeeper Client的代码，如果配置了zookeeper访问控制用户名和密码，那么在创建Zookeeper Client时，增加ACL验证数据。

- 相关代码

涉及到的模块与代码：
	
	orientsec-grpc-common 模块：
	新增 com.orientsec.grpc.common.util.DesEncryptUtils
	
	orientsec-grpc-registry 模块：
	新增 com.orientsec.grpc.registry.remoting.curator.ZkACLProvider
	修改 com.orientsec.grpc.registry.remoting.curator.CuratorZookeeperClient#CuratorZookeeperClient

### 22. 程序健壮性

当服务端与zookeeper断开连接、服务注册信息丢失后，如果客户端与服务端连接正常，那么客户端与服务端依然可以正常通信。