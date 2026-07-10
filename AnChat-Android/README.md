# AnChat (Android)

本地优先的 DeepSeek 聊天客户端。**所有数据都存于本机 SQLite（Room）**，唯一联网请求是发往 DeepSeek 的 API（拉取模型列表 + 收发消息）。

## 技术栈
- Kotlin + Jetpack Compose + Material3
- Room（本地持久化）
- OkHttp + SSE（逐 token 流式回复）
- kotlinx.serialization（JSON）
- AndroidX Security（API Key 加密存储，不落明文）
- minSdk 26 / targetSdk 34 / compileSdk 34

## 运行
1. 用 **Android Studio** 打开本目录（`AnChat-Android/`）。
2. 首次打开会按 `gradle/wrapper/gradle-wrapper.properties` 指定的 Gradle 8.9 创建 wrapper；
   若本机已有 Gradle，也可在终端执行 `gradle wrapper`。
3. SDK 路径由 Studio 自动写入 `local.properties` 的 `sdk.dir`（已被 `.gitignore` 忽略，不会入库）。
4. 用模拟器或真机（Android 8.0+）运行 `app` 模块。

> 本沙箱无 Android SDK，源码按可编译结构编写，请在 Android Studio 中构建/运行。

## 使用
- **设置页**填入 DeepSeek API Key —— 仅保存在本机加密存储，不上传任何第三方。
- 可选「从 API 拉取模型」刷新模型列表，并选择默认模型。
- **对话页**输入消息即流式显示回复；**历史页**管理会话（打开 / 删除）。

## 与 Spring Boot 版的关系
本目录是 AnChat 的安卓重写实现。复用了原 Spring Boot 版的：
- DeepSeek 请求体构造与 SSE 流式逻辑
- 模型定义（对应原 `ModelEnums`：deepseek-v4-pro / flash / flash-chat）
- 数据模型（会话 / 消息 / 模型 → Room `@Entity`）

丢弃了：Thymeleaf + jQuery 网页、Controller / 路由、MySQL + JPA、服务端鉴权（auth / Redis）。
