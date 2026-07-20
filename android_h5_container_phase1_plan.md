# Android H5 容器应用一期开发方案

## 1. 项目背景

本项目计划开发一个 Android 应用，主要用于运行 H5 页面，同时向网页提供 Cordova 插件能力。

整体规划分为两期：

- **一期**：只实现内置 H5 模式，H5 页面随 App 打包，运行在 Cordova WebView 中。
- **二期**：支持远程下载 H5 包、版本管理、校验、回滚和动态加载。

一期目标是先打通核心闭环：

```txt
Android App 启动
→ 加载内置 H5 页面
→ H5 调用 Native 插件
→ Native 返回结果给 H5
```

---

## 2. 一期目标

### 2.1 核心目标

开发一个稳定的 Android H5 容器应用，具备以下能力：

1. App 启动后加载内置 H5 页面。
2. H5 页面运行在 Cordova WebView 中。
3. H5 可以通过 Cordova 插件调用 Android 原生能力。
4. Native 可以将结果回调给 H5。
5. 提供统一的 JS SDK，避免业务页面直接使用 `cordova.exec`。
6. 具备基础安全限制和调试能力。

### 2.2 一期不做的内容

一期明确不做以下能力：

```txt
1. 远程 H5 包下载
2. H5 热更新
3. 灰度发布
4. 多 H5 应用管理
5. 复杂权限系统
6. 插件动态注册
7. 复杂页面路由容器
8. React Native / Flutter 混合
```

---

## 3. 一期整体架构

```txt
Android App
├─ MainActivity
│  └─ Cordova WebView
│
├─ assets/www/
│  ├─ index.html
│  ├─ cordova.js
│  ├─ js/
│  ├─ css/
│  └─ static/
│
├─ Native Plugins
│  ├─ AppRuntimePlugin
│  ├─ DevicePlugin
│  └─ StoragePlugin
│
├─ JS SDK
│  └─ window.NativeBridge
│
└─ 基础能力
   ├─ WebView 配置
   ├─ Android 权限处理
   ├─ 插件调用日志
   ├─ 错误页
   └─ Debug 开关
```

---

## 4. 技术路线

### 4.1 推荐方案

一期建议使用 **标准 Cordova Android 工程** 作为基础。

原因：

```txt
1. 工程结构成熟
2. 插件机制现成
3. cordova.js 自动注入
4. 权限、生命周期、回调机制完整
5. 后续可以逐步改造成更强的原生宿主 App
```

### 4.2 技术栈

```txt
Android：Java / Kotlin
Web 容器：Cordova Android WebView
插件机制：Cordova Plugin
H5 页面：HTML / CSS / JavaScript / Vue / React 均可
JS Bridge：自定义 window.NativeBridge
一期页面来源：App 内置 www 目录
二期页面来源：本地动态 H5 包 / 远程 URL
```

---

## 5. 一期工程结构

```txt
h5-container-app/
├─ config.xml
├─ package.json
├─ www/
│  ├─ index.html
│  ├─ js/
│  │  ├─ native-bridge.js
│  │  └─ app.js
│  ├─ css/
│  │  └─ app.css
│  └─ static/
│
├─ plugins/
│  └─ cordova-plugin-app-runtime/
│     ├─ plugin.xml
│     ├─ www/
│     │  └─ AppRuntime.js
│     └─ src/
│        └─ android/
│           └─ AppRuntimePlugin.java
│
└─ platforms/
   └─ android/
```

---

## 6. 核心模块设计

## 6.1 H5 页面模块

一期 H5 页面全部内置在 App 中。

入口文件：

```txt
www/index.html
```

示例：

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta
    name="viewport"
    content="width=device-width, initial-scale=1, maximum-scale=1"
  />
  <title>H5 Container</title>
</head>
<body>
  <h1>H5 Container</h1>

  <button id="btnDevice">获取设备信息</button>

  <pre id="result"></pre>

  <script src="cordova.js"></script>
  <script src="js/native-bridge.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

注意：

```txt
cordova.js 必须在 native-bridge.js 之前加载。
业务代码需要等待 deviceready 后再调用 Native 能力。
```

---

## 6.2 JS SDK 模块

不要让业务页面直接到处写 `cordova.exec`，建议封装统一 SDK。

文件：

```txt
www/js/native-bridge.js
```

示例：

```js
(function () {
  function exec(service, action, params) {
    return new Promise(function (resolve, reject) {
      if (!window.cordova || !cordova.exec) {
        reject({
          success: false,
          code: "CORDOVA_NOT_READY",
          message: "Cordova is not ready"
        });
        return;
      }

      cordova.exec(
        resolve,
        reject,
        service,
        action,
        [params || {}]
      );
    });
  }

  window.NativeBridge = {
    getRuntimeInfo: function () {
      return exec("AppRuntime", "getRuntimeInfo");
    },

    getDeviceInfo: function () {
      return exec("AppRuntime", "getDeviceInfo");
    },

    toast: function (message) {
      return exec("AppRuntime", "toast", {
        message: message
      });
    },

    closeApp: function () {
      return exec("AppRuntime", "closeApp");
    },

    openExternalUrl: function (url) {
      return exec("AppRuntime", "openExternalUrl", {
        url: url
      });
    }
  };
})();
```

业务页面使用示例：

```js
document.addEventListener("deviceready", function () {
  document.getElementById("btnDevice").onclick = async function () {
    try {
      const info = await window.NativeBridge.getDeviceInfo();
      document.getElementById("result").innerText =
        JSON.stringify(info, null, 2);
    } catch (err) {
      document.getElementById("result").innerText =
        JSON.stringify(err, null, 2);
    }
  };
});
```

---

## 6.3 Cordova 插件模块

一期建议插件名：

```txt
cordova-plugin-app-runtime
```

插件服务名：

```txt
AppRuntime
```

一期插件 API：

```txt
AppRuntime.getRuntimeInfo()
AppRuntime.getDeviceInfo()
AppRuntime.toast(message)
AppRuntime.closeApp()
AppRuntime.openExternalUrl(url)
```

---

## 6.4 Android 插件实现示例

```java
package com.example.appruntime;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AppRuntimePlugin extends CordovaPlugin {

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext
    ) throws JSONException {

        switch (action) {
            case "getRuntimeInfo":
                getRuntimeInfo(callbackContext);
                return true;

            case "getDeviceInfo":
                getDeviceInfo(callbackContext);
                return true;

            case "toast":
                toast(args, callbackContext);
                return true;

            case "closeApp":
                closeApp(callbackContext);
                return true;

            case "openExternalUrl":
                openExternalUrl(args, callbackContext);
                return true;

            default:
                callbackContext.error(makeError("UNKNOWN_ACTION", "Unknown action: " + action));
                return true;
        }
    }

    private void getRuntimeInfo(CallbackContext callbackContext) throws JSONException {
        JSONObject data = new JSONObject();

        data.put("appVersion", "1.0.0");
        data.put("containerVersion", "1.0.0");
        data.put("h5Mode", "builtin");
        data.put("h5Version", "1.0.0");
        data.put("platform", "android");

        JSONArray apis = new JSONArray();
        apis.put("getRuntimeInfo");
        apis.put("getDeviceInfo");
        apis.put("toast");
        apis.put("closeApp");
        apis.put("openExternalUrl");

        data.put("supportedApis", apis);

        callbackContext.success(makeSuccess(data));
    }

    private void getDeviceInfo(CallbackContext callbackContext) throws JSONException {
        JSONObject data = new JSONObject();

        data.put("brand", Build.BRAND);
        data.put("model", Build.MODEL);
        data.put("manufacturer", Build.MANUFACTURER);
        data.put("systemVersion", Build.VERSION.RELEASE);
        data.put("sdkInt", Build.VERSION.SDK_INT);

        callbackContext.success(makeSuccess(data));
    }

    private void toast(JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.optJSONObject(0);
        String message = params != null ? params.optString("message", "") : "";

        cordova.getActivity().runOnUiThread(() -> {
            Toast.makeText(cordova.getActivity(), message, Toast.LENGTH_SHORT).show();
        });

        callbackContext.success(makeSuccess(null));
    }

    private void closeApp(CallbackContext callbackContext) throws JSONException {
        cordova.getActivity().finish();
        callbackContext.success(makeSuccess(null));
    }

    private void openExternalUrl(JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.optJSONObject(0);
        String url = params != null ? params.optString("url", "") : "";

        if (url == null || url.length() == 0) {
            callbackContext.error(makeError("INVALID_URL", "url is empty"));
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        cordova.getActivity().startActivity(intent);

        callbackContext.success(makeSuccess(null));
    }

    private JSONObject makeSuccess(JSONObject data) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("data", data == null ? JSONObject.NULL : data);
        return result;
    }

    private JSONObject makeError(String code, String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("success", false);
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
```

---

## 7. 插件接口规范

### 7.1 成功返回

统一格式：

```json
{
  "success": true,
  "data": {}
}
```

示例：

```json
{
  "success": true,
  "data": {
    "brand": "Xiaomi",
    "model": "xxx",
    "systemVersion": "14"
  }
}
```

### 7.2 失败返回

统一格式：

```json
{
  "success": false,
  "code": "UNKNOWN_ACTION",
  "message": "Unknown action"
}
```

---

## 8. 一期 API 设计

## 8.1 NativeBridge.getRuntimeInfo

调用：

```js
const info = await window.NativeBridge.getRuntimeInfo();
```

返回：

```json
{
  "success": true,
  "data": {
    "appVersion": "1.0.0",
    "containerVersion": "1.0.0",
    "h5Mode": "builtin",
    "h5Version": "1.0.0",
    "platform": "android",
    "supportedApis": [
      "getRuntimeInfo",
      "getDeviceInfo",
      "toast",
      "closeApp",
      "openExternalUrl"
    ]
  }
}
```

---

## 8.2 NativeBridge.getDeviceInfo

调用：

```js
const info = await window.NativeBridge.getDeviceInfo();
```

返回：

```json
{
  "success": true,
  "data": {
    "brand": "Xiaomi",
    "model": "xxx",
    "manufacturer": "Xiaomi",
    "systemVersion": "14",
    "sdkInt": 34
  }
}
```

---

## 8.3 NativeBridge.toast

调用：

```js
await window.NativeBridge.toast("保存成功");
```

返回：

```json
{
  "success": true,
  "data": null
}
```

---

## 8.4 NativeBridge.closeApp

调用：

```js
await window.NativeBridge.closeApp();
```

返回：

```json
{
  "success": true,
  "data": null
}
```

---

## 8.5 NativeBridge.openExternalUrl

调用：

```js
await window.NativeBridge.openExternalUrl("https://example.com");
```

返回：

```json
{
  "success": true,
  "data": null
}
```

---

## 9. 一期开发计划

## 9.1 第 1 阶段：工程初始化

目标：App 能启动并显示内置 H5 页面。

任务：

```txt
1. 创建 Cordova 项目
2. 添加 Android 平台
3. 配置包名、应用名、图标
4. 放入 www/index.html
5. Android 真机运行
6. 确认 deviceready 正常触发
```

验收标准：

```txt
App 安装成功
启动后显示内置 index.html
控制台能看到 deviceready 触发
```

---

## 9.2 第 2 阶段：插件工程搭建

目标：H5 能调用 Android 原生方法。

任务：

```txt
1. 创建 cordova-plugin-app-runtime
2. 编写 plugin.xml
3. 编写 Android 插件类 AppRuntimePlugin
4. 实现 getRuntimeInfo
5. H5 侧通过 cordova.exec 调用
6. 页面展示 Native 返回结果
```

验收标准：

```txt
点击页面按钮
可以调用 Native
页面展示 appVersion、platform、supportedApis
```

---

## 9.3 第 3 阶段：JS SDK 封装

目标：业务页面不直接接触 `cordova.exec`。

任务：

```txt
1. 新建 native-bridge.js
2. 封装 Promise 调用
3. 暴露 window.NativeBridge
4. 增加 getRuntimeInfo、getDeviceInfo、toast
5. 统一错误格式
```

验收标准：

```txt
H5 可以直接调用 window.NativeBridge.getDeviceInfo()
异常时能进入 catch
未 deviceready 时有明确错误
```

---

## 9.4 第 4 阶段：基础 Native 能力

目标：完成一批一期必要能力。

建议一期基础能力：

```txt
1. getRuntimeInfo：获取容器信息
2. getDeviceInfo：获取设备信息
3. toast：弹 Toast
4. closeApp：关闭页面 / App
5. openExternalUrl：用系统浏览器打开外链
```

建议暂缓能力：

```txt
1. 相机
2. 定位
3. 扫码
4. 文件上传
5. 通讯录
6. 剪贴板
```

这些能力涉及系统权限和机型差异，建议二期或后续专项处理。

---

## 9.5 第 5 阶段：安全与限制

虽然一期只加载内置 H5，但安全边界要先留好。

任务：

```txt
1. 禁止任意外链在当前 WebView 内打开
2. 外链统一走系统浏览器
3. 插件调用增加日志
4. Debug 包开启 WebView 调试
5. Release 包关闭 WebView 调试
6. AndroidManifest 权限最小化
```

---

## 9.6 第 6 阶段：打包与测试

任务：

```txt
1. Debug 包测试
2. Release 包测试
3. 真机测试
4. Android 版本兼容测试
5. 页面刷新测试
6. 返回键测试
7. 横竖屏测试
8. 插件异常测试
```

验收标准：

```txt
冷启动正常
返回键行为正常
插件调用正常
断网也能打开内置页面
Release 包不可调试 WebView
```

---

## 10. 一期里程碑

## M1：壳子跑起来

交付物：

```txt
Android App
内置 H5 页面
可安装 APK
```

验收：

```txt
App 打开能看到 index.html
```

---

## M2：插件打通

交付物：

```txt
AppRuntime 插件
getRuntimeInfo API
getDeviceInfo API
```

验收：

```txt
H5 点击按钮能获取 Native 返回数据
```

---

## M3：JS SDK 完成

交付物：

```txt
native-bridge.js
Promise 风格调用
统一错误处理
```

验收：

```txt
业务 H5 不直接写 cordova.exec
```

---

## M4：一期可发布版本

交付物：

```txt
APK
源码
插件说明文档
H5 调用文档
测试用例
```

验收：

```txt
内置页面稳定运行
插件能力可用
基础安全限制完成
```

---

## 11. 推荐排期

### 1 周 MVP 排期

```txt
第 1-2 天：
Cordova Android 工程搭建，跑通内置页面

第 3-4 天：
自定义插件 AppRuntime，跑通 JS 调 Native

第 5 天：
封装 window.NativeBridge SDK

第 6 天：
补基础能力：toast、deviceInfo、closeApp、externalUrl

第 7 天：
安全限制、错误页、日志、真机测试、打包
```

### 2 周稳定版排期

```txt
第 1 周：
主链路跑通

第 2 周：
补测试、文档、异常处理、兼容性
```

---

## 12. 一期最终交付清单

```txt
1. Android APK
2. Cordova Android 项目源码
3. 内置 H5 Demo 页面
4. AppRuntime Cordova 插件
5. native-bridge.js
6. H5 调 Native API 文档
7. Android 插件开发说明
8. 一期测试用例
9. 二期扩展方案说明
```

---

## 13. 二期预留设计

一期虽然不做远程下载，但可以预留概念。

### 13.1 一期写死

```txt
h5Mode = builtin
entryUrl = file:///android_asset/www/index.html
```

### 13.2 二期扩展

```txt
h5Mode = builtin | local_package | remote_url
entryUrl = file:///data/data/包名/files/h5/v12/index.html
```

建议一期就定义并返回以下字段：

```json
{
  "h5Mode": "builtin",
  "h5Version": "1.0.0"
}
```

这样二期只改实现，不改 H5 API。

---

## 14. 二期规划方向

二期可以增加以下能力：

```txt
1. manifest.json 检查
2. zip 包下载
3. sha256 校验
4. 解压到 App 私有目录
5. 加载本地离线包
6. 失败回滚 assets 内置包
7. H5 包版本管理
8. 插件版本兼容判断
```

二期启动流程：

```txt
启动 App
├─ 检查本地是否有可用 H5 包
│  ├─ 有：加载本地包
│  └─ 无：加载 assets 内置包
│
└─ 后台检查远程 manifest
   ├─ 有新版本：下载
   ├─ 校验成功：下次启动切换
   └─ 失败：继续使用旧包
```

---

## 15. 最终建议

一期方案建议定为：

```txt
标准 Cordova Android App
+ 内置 assets/www H5 页面
+ 自定义 AppRuntime 插件
+ window.NativeBridge JS SDK
+ 基础安全限制
```

一期核心指标只有三个：

```txt
能打开
能调用
能回调
```

这三个稳定后，再进入二期做远程 H5 包下载、版本管理和回滚。

