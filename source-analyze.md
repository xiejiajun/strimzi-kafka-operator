### 基础铺垫
- https://vertx.io/docs/vertx-core/java/
- https://github.com/vert-x3/vertx-awesome
- [Vertx学习一：这玩意是到底是个啥](https://blog.csdn.net/lizhou828/article/details/93297153)
- [Vert.x(vertx) 简明介绍](https://blog.csdn.net/king_kgh/article/details/80772657)
- [vert.x相比spring全家桶系列,除了性能外,还有什么优势?](https://www.zhihu.com/question/277219881)
- [Vertx - Future.compose](https://www.jianshu.com/p/38acf2cc6f2f): 必看
- [vert.x中future的简单使用](https://blog.csdn.net/qq_38366063/article/details/105906296)
    - https://blog.csdn.net/qq_38366063/category_9972305.html
- [用vertx compose写链式操作](https://blog.51cto.com/kankan/1929999)
- [Vert.x及响应式编程](https://www.jianshu.com/p/3dbd5dff486b)
- [vertx初探](https://segmentfault.com/a/1190000021036621?utm_source=tag-newest): 必看
- [vertx 4.0的 CompositeFuture.all调用备忘](https://blog.csdn.net/wbkys/article/details/115374703)
- [Vert.x 3 核心手册之开头篇](https://www.jianshu.com/p/68a33b610fac)
- [vert.x](https://blog.csdn.net/z449077880/category_10683523.html)
    - [【java】vertx从入门到放弃——入门（一）](https://blog.csdn.net/z449077880/article/details/111571754)
    - [【java】vertx从入门到放弃——入门（二）Verticle](https://blog.csdn.net/z449077880/article/details/111603445)
    - [【java】vertx从入门到放弃——入门（三）EventBus](https://blog.csdn.net/z449077880/article/details/111630990)
    - [【java】vertx从入门到放弃——入门（四）Codec](https://blog.csdn.net/z449077880/article/details/111660439)
    - [【java】vertx从入门到放弃——入门（五）cluster](https://blog.csdn.net/z449077880/article/details/111693768)
    - [【java】vertx从入门到放弃——入门（六）Promise](https://blog.csdn.net/z449077880/article/details/111830185)
    - [【java】vertx从入门到放弃——入门（七）Future](https://blog.csdn.net/z449077880/article/details/111879421)
    - [【java】vertx从入门到放弃——入门（八）HA](https://blog.csdn.net/z449077880/article/details/111941718)
    - [【java】vertx从入门到放弃——入门（九）Cassandra](https://blog.csdn.net/z449077880/article/details/112230822)
- [vert.x中CompositeFuture的源码解析](https://zhuanlan.zhihu.com/p/36012276)
    - CompositeFuture.join: 批量处理多个Future

---
### 重要入口
- io.strimzi.operator.cluster.Main
- io.strimzi.operator.cluster.operator.assembly.KafkaAssemblyOperator.reconcile
- 该Operator主要逻辑流程：
    - io.strimzi.operator.cluster.ClusterOperator#start -> io.strimzi.operator.common.AbstractOperator#createWatch -> vertx.setPeriodic(reconciliationIntervalMs, res2 -> reconcileAll("timer")): 周期性调用reconcileAll进行资源调和 -> io.strimzi.operator.cluster.ClusterOperator#reconcileAll -> io.strimzi.operator.common.Operator#reconcileAll -> io.strimzi.operator.common.Operator#reconcileThese -> io.strimzi.operator.common.AbstractOperator#reconcile -> io.strimzi.operator.cluster.operator.assembly.KafkaAssemblyOperator#createOrUpdate -> io.strimzi.operator.cluster.operator.assembly.KafkaAssemblyOperator#reconcile -> 基于vertx-core的Future.compose链式调用实现一系列调和逻辑
    
---
### RetryWatch机制实现原理
- io.strimzi.operator.cluster.ClusterOperator#start -> operator.createWatch(namespace, operator.recreateWatch(namespace)) -> AbstractOperator.createWatch(namespace, AbstractOperator.recreateWatch(namespace)): operator.recreateWatch作为operator.createWatch的onClose参数传入，Watch客户端在退出时会调用operator.recreateWatch， 这就实现了RetryWatch机制
