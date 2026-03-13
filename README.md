# RutoPhoneDex

`RutoPhoneDex` 是一个面向 `adb shell` 场景的小型 Java 工具。它的目的不是做 APK UI，而是为了在设备 shell 环境里，更方便地拿到只有 Android Java 层才容易访问的信息，例如：

- `Context`
- `PackageManager`
- 已安装应用列表
- 应用 label、uid、是否系统应用、apk 路径

它通过 `io.github.iamr0s:app-process` 提供的 `NewProcess.getUIDContext` 获取 `Context`，然后在 `/system/bin/app_process` 中直接执行 `dex.rutophone.Main`。

## 当前功能

- `--help`
- `--version`
- `list-apps`
- `list-apps --include-system`
- `list-apps --no-label`
- `list-apps --uid`
- `list-apps --is-system`
- `list-apps --apk-path`

默认行为：

- 默认不输出系统应用
- 默认输出 `label`

## 构建

快速构建方式：

```sh
sh scripts/build-dex.sh
```

这个脚本会：

- 清理旧的 `app/build/rutophone/` 和 `out/`
- 下载构建依赖到 `.builddeps/`
- 在 `app/build/rutophone/` 下生成中间编译产物
- 在 `out/` 下生成最终产物

最终产物：

- [rutophone.dex](/workspaces/RutoPhoneDex/out/rutophone.dex)
- [rutophone.jar](/workspaces/RutoPhoneDex/out/rutophone.jar)

其中，`out/rutophone.jar` 是给 `app_process -Djava.class.path=...` 直接使用的文件。

## 版本号

基础版本号写在 [VERSION](/workspaces/RutoPhoneDex/VERSION)。

构建时会自动拼接当前 git short id，生成类似下面这样的展示版本：

```text
0.1.0-r8316181
```

生成的 `BuildConfig.java` 不直接放在源码目录，而是基于模板 [BuildConfig.java.template](/workspaces/RutoPhoneDex/templates/BuildConfig.java.template) 生成到 `app/build/rutophone/generated-src/`。

## 使用方式

推荐流程是先把最终 jar 推到设备，再从 `adb shell` 中执行。

1. 构建：

```sh
sh scripts/build-dex.sh
```

2. 推送到设备：

```sh
adb push out/rutophone.jar /data/local/tmp/rutophone.jar
```

3. 在设备上执行：

查看帮助：

```sh
adb shell /system/bin/app_process -Djava.class.path=/data/local/tmp/rutophone.jar /system/bin dex.rutophone.Main --help
```

查看版本：

```sh
adb shell /system/bin/app_process -Djava.class.path=/data/local/tmp/rutophone.jar /system/bin dex.rutophone.Main --version
```

列出非系统应用：

```sh
adb shell /system/bin/app_process -Djava.class.path=/data/local/tmp/rutophone.jar /system/bin dex.rutophone.Main list-apps
```

列出所有应用，并输出更多字段：

```sh
adb shell /system/bin/app_process -Djava.class.path=/data/local/tmp/rutophone.jar /system/bin dex.rutophone.Main list-apps --include-system --uid --is-system --apk-path
```

## 输出示例

```text
package=com.example.demo | label=Demo | uid=10234 | is_system=false | apk_path=/data/app/~~xxx/com.example.demo-xxx/base.apk
count=1
```

## 仓库说明

- [Main.java](/workspaces/RutoPhoneDex/app/src/main/java/dex/rutophone/Main.java)
  `app_process` 入口和命令行逻辑
- [build-dex.sh](/workspaces/RutoPhoneDex/scripts/build-dex.sh)
  一键构建 `dex` 和 `jar`
- [BuildConfig.java.template](/workspaces/RutoPhoneDex/templates/BuildConfig.java.template)
  版本信息模板

## 提交与忽略

应该提交的内容：

- `scripts/build-dex.sh`
- `VERSION`
- `templates/BuildConfig.java.template`
- `app/src/...`
- Gradle 配置
- `README.md`

已经忽略的生成内容：

- `.builddeps/`
- `app/build/`
- `out/`
