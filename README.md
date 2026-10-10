# Sure-Xu

[![License](https://img.shields.io/github/license/yu1989324402/Xu.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/yu1989324402/Xu)](https://github.com/yu1989324402/Xu/releases)
[![Downloads](https://img.shields.io/github/downloads/yu1989324402/Xu/total)](https://github.com/yu1989324402/Xu/releases)

> **未来拟态 · 纯白视觉 · 一个 App 管住所有任务**

Sure-Xu 是一套面向支付宝芝麻粒生态的**免 root 自动化模块**：由 LSPosed 等框架注入支付宝进程，把每天重复的收取、喂养、签到、领任务、浇水全部托管；配套控制台 App 用 Jetpack Compose 重写，负责开关、参数、日志与数据统计。

任务全部由抓包接口驱动，纯协议调用，不依赖无障碍与模拟点击。

## 界面特色

| 特性 | 说明 |
| --- | --- |
| 未来拟态纯白 | 全局纯白基面，功能元素以柔和高光/投影浮起（`neuRaised`），主色电光蓝 `#3D82FF` → 量子青 `#12B5A5` 渐变 |
| 悬浮胶囊导航 | 首页 / 日志 / 配置 / 设置 四页收进一枚两侧留边的悬浮胶囊，选中项凹陷高亮 |
| 能量统计卡 | 顶部激活状态 + 版本号，下方 4×4 网格（总量 / 今年 / 本月 / 今日 × 收取 / 帮收 / 浇水），跨年不重置 |
| 一功能一卡片 | 配置页与日志页每个功能独立一张浮起卡片，点整行进详情，开关即改即生效 |
| 日志体验 | 运行 / 抓包 / 异常分类型查看，支持复制本条、复制全部、一键滑到底 |
| 账号管理 | 多账号配置隔离，头像弹窗切换；配置支持导入导出 |
| 图标与关于页 | 深空蓝 → 量子青渐变自适应图标（含 monochrome 主题图标层），内置项目介绍与版本下载入口 |

## 能力一览

按配置页分组组织，**每个功能都能单独开关**：

| 分组 | 模块 |
| --- | --- |
| 基础 | 基础（执行间隔、定时执行、账号、备份等底座配置） |
| 森林 | 森林（能量收取 / 帮收 / 浇水 / 道具） |
| 庄园 | 庄园（喂养、任务） |
| 新村 | 新村 |
| 农场 | 农场、**饿了么果园**（收水滴 / 签到奖励 / 阳光卡任务 / 多轮浏览 / 自动浇水） |
| 金豆 | 金豆夺宝 |
| 运动 | 运动 |
| 会员 | 会员 |
| 其他 | 物种、海洋、保护、经营、消费金、AI答（需自配接口） |

> 下单类与真实社交类任务不会代替用户执行，配置页日志会说明跳过原因。

## 特色功能

| 特性 | 说明 |
| --- | --- |
| AI 桌宠 | Q 版鲸鱼娘悬浮窗，7 态表情自主活动，可拖动置顶；点击展开对话窗，运行状态联动显隐，支持首页隐藏 |
| 自动切号 | 多账号调度器按历史账号轮询切换（默认关闭，不影响常规执行）：任务全部空闲才切入、切入前 15s 探测、7200s 防风控冷却；支持静默轮切广播触发 |

## 抓包与日志

「抓包记录」覆盖四条链路，用于接口调试与问题定位：

| 链路 | 覆盖范围 |
| --- | --- |
| ariver RPC | 小程序 RV 接口层 |
| MTOP | MtopBuilder 全部 sync / async 请求 |
| okhttp | 宿主原生 HTTP 请求 |
| WebView | H5 页面请求 |

开启方式：基础配置打开「开启抓包(基于新接口)」+「使用新接口」，再到日志页打开「抓包记录」。

日志页另有「查看运行日志」「查看异常日志」，每类可独立开关，均支持复制与一键滑到底。

## 下载

最新版 **v3.1.7**（单包通吃，安装一个即可）：

| 版本 | 适用环境 | APK |
| --- | --- | --- |
| **Normal** | 单包双入口：libxposed API 102（LSPosed 等免 Root 新框架）+ 传统 Xposed API 82~93（`assets/xposed_init`，Root / 传统框架） | [Sure-Xu-Normal-3.1.7.apk](https://github.com/yu1989324402/Xu/releases/download/v3.1.7/Sure-Xu-Normal-3.1.7.apk) |

每个 APK 附带同名 `.sha256` 校验文件，全部版本见 [Releases](https://github.com/yu1989324402/Xu/releases)。

## 安装

1. 设备安装 LSPosed 等免 root 框架（按框架说明完成激活）；Root / 传统框架直接启用模块
2. 安装 Normal APK
3. 在框架中勾选作用域：**支付宝**
4. 强制停止支付宝后重新打开，模块自动注入
5. 打开 Xu：**首页**看运行状态与数据统计 → **配置**里调任务参数 → **日志**里查执行记录

## 技术栈

| 层 | 实现 |
| --- | --- |
| 模块框架 | 单包双入口：libxposed API 102（`META-INF/xposed` 声明）+ 传统 Xposed API ≤93（`assets/xposed_init` 声明） |
| UI | Jetpack Compose + Miuix，叠加自研拟态设计层 `Neumorphic.kt` / `PureWhiteTheme.kt` |
| 任务引擎 | 模型驱动：`ModelOrder` 注册 → `ModelTask` 主循环 → `ModelFields` 配置驱动，字段变更自动持久化并即时生效 |
| 接口层 | 宿主 mtopsdk 反射调用（MTOP）+ 原生 RPC 桥（New / Old Bridge），登录态、wua 与签名交由宿主处理 |
| 构建 | Gradle 9.4 / AGP 9.2 / Kotlin 2.4；GitHub Actions 云端出包，两 flavor 自动签名 |

## 本地构建

```bash
# 环境：JDK 17+ / Android SDK（platforms;android-37、build-tools）
# 使用本地预装 Gradle 发行版构建（勿用 ./gradlew wrapper，发行版下载易超时）
gradle assembleNormalRelease -Pversion=3.1.7
# 产物：app/build/outputs/apk/normal/release/Sure-Xu-Normal-3.1.7.apk（已签名）
```

签名读取 `app/keystore.properties`（不入库，首次克隆后需自备）：

```properties
storeFile=your-release.jks
storePassword=你的口令
keyAlias=你的别名
keyPassword=你的口令
```

正式发版走 `.github/workflows/release.yml`：打 tag → 云端构建单包（normal）→ 上传 Release 资产。

## 常见问题

- **开关改了不生效**：配置页改动会广播重载；若仍无效，强制停止支付宝后重进一次。
- **抓包记录为空**：确认「开启抓包(基于新接口)」+「使用新接口」+「抓包记录」三处均已打开，并重启过支付宝。
- **数据目录在哪**：`Android/media/com.eg.android.AlipayGphone/Sure-Xu/`（配置、名单、统计、城市码、日志）；旧目录首次启动自动迁移，无需手动搬运。
- **框架怎么选**：单包双入口，免 Root（LSPosed 等 libxposed 102）与 Root / 传统框架（API 82~93）均可直接用。
- **日志占空间**：日志页每类记录可单独关闭，关闭时清理该类历史记录。

## 使用条款

1. 本项目仅供学习与研究使用，不得用于任何形式的商业行为。
2. 使用者因违反本声明规定而触犯法律的，后果自负，作者不承担责任。
3. 本项目完全免费开源，请勿二次贩卖。
4. 本 APP 是为了学习研究用，不得进行任何形式的转发、发布、传播。请于 24 小时内卸载本 APP。若使用期间造成任何损失，作者不负任何责任。本 APP 不篡改、不修改、不获取任何个人信息及其支付宝信息。本 APP 使用者因为违反本声明的规定而触犯中华人民共和国法律的，一切后果自负，作者不承担任何责任。凡以任何方式直接、间接使用 APP 者，视为自愿接受本声明的约束。本 APP 如无意中侵犯了某个媒体或个人的知识产权，请来信或来电告之，作者将立即删除。

## 授权

本项目以 **GPL-3.0** 授权：**禁止**商业用途，**禁止**二次修改后闭源发布。第三方组件许可证原文见 [licenses/](licenses/) 目录。

### 修改声明（GPL-3.0 第 5(a) 条）

本仓库是上游项目 [aw1y2z/Sesame-M](https://github.com/aw1y2z/Sesame-M)（GPL-3.0）的**修改版本（modified version）**，在其源码基础上完成包名与应用名迁移、界面重构（未来拟态纯白 UI）、任务模块增删与接口适配等修改，修改自 2025 年起持续进行，具体修改内容以本仓库的提交历史为准。

依据 GPL-3.0：本修改版本同样以 GPL-3.0 发布，保留上游版权声明与许可证原文（见 [LICENSE](LICENSE) 与 [licenses/](licenses/)），并在此显著声明本作品已被修改。任何再分发者需一并遵守上述许可条款。

