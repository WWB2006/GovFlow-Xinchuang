# GovFlow-Xinchuang (政务协同与信创审批流转服务)

## 📌 项目定位与背景
针对智慧政务服务中心与政企办公流转中的核心业务场景，聚焦**信创环境（达梦DM8/人大金仓/SQL Server）数据库平滑迁移**、**政务审批高并发事务流转**、**分布式审批互斥防并发冲突**与**Kafka审计日志可靠投递**而设计的高可靠协同服务。

本工程严格对标信创政企（如南威软件等）招聘要求与企业级生产规范，无任何过度包装或AI生硬词汇，全部设计均配有真实的单元测试与基准压测用例。

---

## 🛠️ 核心技术栈与架构
- **核心框架**：Spring Boot 2.7.18 / Spring Framework 5.3.31
- **API 文档与契约**：Knife4j 3.0.3 (基于 Swagger 2.0 / OpenAPI 2)
- **数据持久化与数据库**：Spring JDBC / H2 Database (MySQL 兼容模式仿真)
- **信创数据库方言适配**：达梦 DM8 / SQL Server 抽象方言转换层（AST正则与词法映射）
- **高并发与分布式控制**：
  - 基于 Redis 语义的分布式审批互斥锁（支持守护线程 Watchdog 1000ms 自动续期，防超时误释放）
  - UUID 幂等令牌管理器（Token 机制，原子消费防止前端重复点击与重放）
  - 审批预约配额 CAS 乐观锁版本控制（防超卖，高并发无死锁）
- **可靠消息投递**：
  - 本地事务表（Transactional Outbox 模式）
  - Kafka 消息投递模拟（`acks=all` 级别可靠性保障、重试机制）
  - 消费者端幂等消费与手动位移提交（`manual_immediate` 语义）
- **数据库性能与慢SQL优化**：10,000条合成政务数据慢SQL基准压测与 `EXPLAIN` 执行计划对比

---

## 📂 项目模块结构
```text
GovFlow-Xinchuang/
├── pom.xml                                 # Maven 构建配置 (Spring Boot 2.7.18 + Knife4j + H2)
├── src/
│   ├── main/
│   │   ├── java/com/wwb/govflow/
│   │   │   ├── GovFlowApplication.java     # 主启动类
│   │   │   ├── config/                     # Knife4j 与 Web 跨域静态资源配置
│   │   │   │   ├── Knife4jConfig.java
│   │   │   │   └── WebMvcConfig.java
│   │   │   ├── dialect/                    # 信创达梦DM8/SQL Server方言转换器
│   │   │   │   ├── DialectType.java
│   │   │   │   └── XinchuangDialectTranslator.java
│   │   │   ├── lock/                       # 分布式互斥锁与看门狗自动续期
│   │   │   │   └── GovDistributedLock.java
│   │   │   ├── idempotent/                 # 防重放与防重复提交幂等令牌管理器
│   │   │   │   └── IdempotencyTokenManager.java
│   │   │   ├── workflow/                   # 审批状态机、记录与预约配额CAS实体
│   │   │   │   ├── ApprovalStatus.java
│   │   │   │   ├── ApprovalStateMachine.java
│   │   │   │   ├── ApprovalRecord.java
│   │   │   │   └── AppointmentSlot.java
│   │   │   ├── outbox/                     # 事务Outbox模式与Kafka可靠投递/幂等消费
│   │   │   │   ├── OutboxMessage.java
│   │   │   │   ├── KafkaReliablePublisher.java
│   │   │   │   └── KafkaAuditConsumer.java
│   │   │   ├── service/                    # 业务核心层与慢SQL基准压测服务
│   │   │   │   ├── GovApprovalService.java
│   │   │   │   └── SlowSqlBenchmarkService.java
│   │   │   └── controller/                 # RESTful 控制器与交互控制面板接口
│   │   │       ├── GovApprovalController.java
│   │   │       ├── BenchmarkController.java
│   │   │       └── DashboardController.java
│   │   └── resources/
│   │       ├── application.yml             # 服务配置 (Port 8087, H2 datasource)
│   │       └── static/
│   │           └── index.html              # 可视化暗黑交互监控仪表盘 (政务协同流转中台)
│   └── test/
│       └── java/com/wwb/govflow/
│           └── GovFlowIntegrationTest.java # 12个覆盖全链路的核心功能集成测试
```

---

## 🚀 快速启动与本地测试

### 1. 运行测试套件 (验证 100% 通过)
在项目根目录下执行 Maven 测试命令：
```bash
mvn test
```
**测试输出指标**：
- Tests run: **12**, Failures: **0**, Errors: **0**, Skipped: **0**
- 覆盖信创方言转换、审批状态机流转、CAS配额扣减防超卖、看门狗自动续期、幂等令牌消费、Kafka Outbox 事务补偿与消费去重。

### 2. 启动服务
使用 IDEA 直接运行 `GovFlowApplication.java` 或通过 Maven 启动：
```bash
mvn spring-boot:run
```

### 3. 在线接口与可视化中台访问
- 📑 **Knife4j 在线 API 文档**：`http://localhost:8087/doc.html`
- 🖥️ **政务协同交互中台 Dashboard**：`http://localhost:8087/`
- 🔍 **慢 SQL 压测验证接口**：`POST http://localhost:8087/api/benchmark/run-slow-sql`
- 🔒 **分布式锁与续期测试**：`POST http://localhost:8087/api/approval/apply`
