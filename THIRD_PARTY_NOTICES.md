# 第三方组件与版权

本项目的 AGPL-3.0-only 声明适用于自有源码。第三方库、模板、字体与资源仍遵守原许可。
本文件是主要来源索引，具体依赖版本以 Maven POM、npm lockfile 和 Python 配置为准；
构建产物的分发者还应携带其中使用的第三方许可证。

| 组件 / 来源 | 用途 | 许可或核对入口 |
| --- | --- | --- |
| [Tencent TDesign Vue Next Starter](https://github.com/Tencent/tdesign-vue-next-starter) | 两个后台的模板基础 | [MIT 全文](LICENSES/TDESIGN-STARTER-MIT.txt) |
| [TDesign Vue Next](https://github.com/Tencent/tdesign-vue-next) | UI 组件 | MIT，随 npm 包提供 |
| [Vue](https://github.com/vuejs/core)、[Vite](https://github.com/vitejs/vite) | Web 框架与构建 | MIT，随 npm 包提供 |
| [Apache ECharts](https://github.com/apache/echarts) | 图表 | Apache-2.0 |
| [Spring Boot](https://github.com/spring-projects/spring-boot)、Spring Cloud | Java 应用与网关 | Apache-2.0 |
| MyBatis-Plus、Apache POI 等 | 持久层与文件处理 | 以各 Maven 依赖的许可证为准 |
| [WenQuanYi Micro Hei](https://github.com/anthonyfok/fonts-wqy-microhei) | 合同 PDF 字体 | 内嵌 Apache-2.0，见 [字体声明](equipment-alipay/src/main/resources/fonts/NOTICE.md) |
| FastAPI、LangChain、LangGraph 等 | 可选 Python Agent | 以安装包的 LICENSE 与元数据为准 |
| [MkDocs](https://github.com/mkdocs/mkdocs)、[Material for MkDocs](https://github.com/squidfunk/mkdocs-material) | 文档站 | BSD-2-Clause / MIT |

后端字体的版权信息及 Apache 2.0 全文保存在字体目录。依赖包不会作为 `node_modules`、虚拟环境或 jar 打包到源码仓库。
Alipay、HRouter、Tencent 等名称属于对应主体；它们的名称用于标明接入能力、来源或赞助关系。
