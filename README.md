# NFC Wallet Compat

用于 **一加 Ace 3（PJE110）/ AlphaDroid 4.7 / Android 16** 的钱包兼容模块，帮助原版一加／OPPO 钱包使用手机内置 eSE 交通卡。

当前版本 **0.6.0**，包名 **`nfc.wallet.compat`**，使用 **libxposed API 102**。这是针对特定手机与系统组合的兼容项目，不是通用 NFC 修复包，也不是一加、OPPO、支付宝或交通卡发行方的官方项目。

**刷交通卡请将默认 NFC 付款应用设为“钱包”，并且只启用一个钱包兼容模块。**

## 下载

在 [Releases](https://github.com/ceigt/nfc-wallet-compat/releases) 下载 APK。源码编译方式见下文。

本项目不提供钱包、账号中心等厂商应用安装包，使用者需要自行准备适合设备的正版应用。

## 适用条件

| 项目 | 当前适配范围 |
| --- | --- |
| 手机 | 一加 Ace 3，型号 `PJE110` |
| 系统 | 已检查的 AlphaDroid 4.7 / Android 16 环境 |
| 框架 | 支持 **现代 libxposed API 102** 的 LSPosed；验证环境为 2.2.1（7912） |
| 钱包 | 原版 `com.finshell.wallet` 5.51.5（versionCode `551053`）；UI 兼容限定此版本 |
| 硬件 | NFC 和内置 eSE；模块限定终端 `eSE1` |

手机需要已具备正常工作的 Root、注入环境和兼容的 LSPosed。本项目不包含刷机或安装 Root 框架教程。

APK 的最低 Android 版本为 9，但 NFC 路由适配代码只在 `PJE110`、Android API 36 及以上尝试安装，且仍会校验系统接口。**这不代表其他机型、Android 版本或未来系统更新已经适配。**

## 安装与使用

1. 安装并准备好原版钱包及其需要的账号服务，自行完成账号登录。不要向他人提供密码或验证码。
2. 安装本项目 APK，打开 LSPosed，启用 **NFC Wallet Compat**。
3. 核对固定作用域，只有以下三项：

   | 应用 | 包名 |
   | --- | --- |
   | 钱包 | `com.finshell.wallet` |
   | NFC Service | `com.android.nfc` |
   | SecureElementApplication | `com.android.se` |

4. 停用旧 **Wallet Alpha Compat** 以及其他同时修改钱包交通卡路由的兼容模块。
5. **整机重启。** 本模块没有热重载功能，更新、停用或切换模块后也需要重启。
6. 打开系统 NFC，将默认钱包／默认 NFC 付款应用设为 **钱包**。
7. 进入钱包的“乘车”页面，按发行方流程开通或选择交通卡；充值和费用以钱包及发行方说明为准。
8. 使用手机 NFC 感应区域靠近闸机或读卡器，首次使用请现场验证。

模块没有独立的设置界面，安装后请在 LSPosed 管理启用状态。它不会替你登录、开卡、充值或完成付款。

## 默认应用与支付宝碰一下

### 默认支付宝，还能刷钱包交通卡吗？

**当前版本应保持默认钱包，不承诺默认支付宝时正常过闸。**

在适配设备上切换默认应用进行路由对照时：

| 默认应用 | 交通卡四条专用 AID 路由 |
| --- | --- |
| 钱包 | 存在，指向 eSE1（`0xC0`，电源状态 `0x3B`） |
| 支付宝 | 从硬件路由表中消失 |
| 恢复钱包 | 四条路由恢复 |

这是设备路由检查结果，不等同于默认支付宝时的实体闸门测试。本模块尚未将交通卡路由与默认付款应用完全分离。

### 默认钱包，会影响支付宝碰一下吗？

在已观察的设备上，默认钱包时，支付宝蓝色“碰一下”设备可以在**亮屏解锁后**完成付款。不要把这理解为所有终端和所有屏幕状态都已验证。

熄屏时曾出现完全无响应、解锁后付款成功的反馈。目前不能确定是默认应用、终端所用 NFC 通道、系统屏幕策略还是其他因素造成的；**不承诺支付宝熄屏兼容**。支付宝的[官方使用说明](https://render.alipay.com/p/c/180020570000133336/index.html)描述的常规条件也是亮屏解锁、开启 NFC。

交通卡与支付宝碰一下使用的 NFC 通道可能不同，交通卡可熄屏使用并不代表支付宝也可熄屏使用。

## 已验证与未验证

截至 2026-10-19：

- **0.6.0 已检查：** 三个作用域实际通过 API 102 加载；钱包主界面和现有深圳通互联互通卡页面正常；显示“靠近读卡器”；四条交通卡专用路由正常；三次冷启动通过，检查期间未发现钱包、NFC 或安全芯片相关崩溃。
- **实体刷卡记录：** 旧基线 0.5.2 有深圳地铁成功刷卡反馈。0.6.0 修改了包名和 Hook API，**尚未独立完成实体闸门复测**，不能把旧版记录当作新版实测。
- **尚未验证：** 香港交通卡／八达通、默认支付宝过闸、所有锁屏／熄屏组合，以及其他手机和 ROM。

“靠近读卡器”和正确的路由表只能说明软件检查通过，不能代替实体闸门验证。

## 更新、迁移与回退

- 从旧包 `io.github.knox.walletalphacompat.test` 迁移时，新包属于独立应用：安装新模块、停用旧模块、核对三个作用域，再重启。无需清除钱包或账号数据。
- 后续更新使用相同包名及相同签名、递增 versionCode 覆盖安装，再重启。
- 如果新版出现问题，在 LSPosed 停用新版，恢复原来可用版本的作用域，再重启。不要同时启用两版。
- 自行编译的 APK 使用自己的签名，不能直接覆盖其他签名的安装包。切换签名前先处理模块安装状态；无需卸载钱包或清除交通卡。

## 工作方式与边界

模块通过 `XposedModule.onPackageReady` 安装现代拦截链，使用 API 102 Hook ID，通过 `META-INF/xposed` 登记入口与固定作用域。不调用旧版 `de.robv.android.xposed` API。

主要兼容逻辑：

- 只忽略钱包缺失特定 Osense 后台提示方法导致的已知 `NoSuchMethodError`，保留其他异常。
- 在原钱包证书、UID、版本、默认付款应用和 eSE 能力检查通过时，兼容缺失的厂商 NFC 选择设置。
- 校验 NFC 接口、设备型号及路由归属后，将已知交通卡 AID 绑定到 eSE1；保留其他 AID，拒绝 SIM 路由冲突，绑定失败时尝试回滚。
- 在安全芯片服务原本因已知 APDU 权限问题拒绝访问时，仅对**证书和调用 UID 均匹配的原版钱包、eSE1 终端**提供兼容处理。它会改变该已校验调用的访问判定，请在理解这一点后使用。

未知接口不会猜测适配，可能需要后续针对性维护。交通卡、账户数据和厂商 APK 不随项目分发。

## 源码构建

当前提供 **Windows / PowerShell** 构建脚本，所需环境：

- JDK 17。
- Android SDK Platform 36（`platforms/android-36/android.jar`）。
- Android SDK Build Tools 36.0.0。
- Python 3，用于 APK 结构检查。

`lib/libxposed-api-102.0.0.jar` 是固定版本的编译依赖，构建时校验 `lib/api.sha256`；框架 API 类不会打入 APK。

示例路径可按本机环境修改：

```powershell
$sdk = 'C:\Android\Sdk'
$java = 'C:\Java\jdk-17\bin'

# 运行策略、接口、回滚和拦截链测试。
.\test.ps1 -AndroidSdk $sdk -JavaBin $java

# 使用自己的密钥。密钥与密码文件请保存在仓库外。
.\build.ps1 `
  -AndroidSdk $sdk `
  -JavaBin $java `
  -SigningKey 'C:\Signing\nfc-wallet-compat.p12' `
  -PasswordFile 'C:\Signing\store-password.txt' `
  -KeyAlias 'nfc-wallet-compat' `
  -OutputApk '.\nfc-wallet-compat-0.6.0.apk'

python .\verify-apk.py .\nfc-wallet-compat-0.6.0.apk
```

`SigningKey` 应为已存在的签名密钥，`PasswordFile` 包含其密码；上述路径不是项目自带文件。发布签名私钥及密码不公开。用其他设备构建时不要期望生成与官方发布 APK 相同的签名字节。

源码目录：

```text
src/nfc/wallet/compat/       模块及兼容策略
tests/nfc/wallet/compat/     JVM 测试
META-INF/xposed/            API 102 入口、配置及作用域
res/                        Android 描述资源
lib/                        编译依赖、散列与第三方许可
build.ps1                   编译、打包、对齐与签名
test.ps1                    自动检查
verify-apk.py               APK 元数据与依赖边界检查
```

## 问题反馈

提交 Issue 时请提供手机型号、ROM 和 Android 版本、LSPosed 版本、钱包版本、模块版本、默认 NFC 应用，以及亮屏／锁屏／熄屏状态和复现时间。日志只保留相关片段，提交前删除账号、卡号、交易信息、令牌等个人信息。

## 许可证与第三方依赖

本项目使用 [Apache License 2.0](LICENSE)。libxposed API 102.0.0 同样使用 Apache-2.0，许可和发布元数据位于 `lib/`。

- [libxposed API 文档](https://jingmatrix.github.io/libxposed-api/io/github/libxposed/api/package-summary.html)
- [API 编译依赖来源](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/)

钱包与账号应用、交通卡及相关商标属于各自权利人，不随本项目分发。
