# H5 录音接口对接文档

## 概述

容器提供原生录音能力，H5 通过 `window.NativeBridge` 调用。默认 AAC (M4A) 格式，44100Hz，128kbps 立体声。录音结束后返回本地文件路径，应用退出时自动清理所有录音文件。

---

## API 列表

| 方法 | 说明 | 返回 |
|------|------|------|
| `requestAudioPermission()` | 请求麦克风权限 | `{granted: boolean}` |
| `startRecording(options?)` | 开始录音 | `{message, format}` |
| `stopRecording()` | 停止录音 | `{filePath, duration, fileSize, format}` |
| `cancelRecording()` | 取消录音并删除文件 | `{message}` |
| `getRecordingState()` | 查询当前录音状态 | `{state, filePath?, duration?}` |
| `onAudioPermissionResult(cb)` | 监听权限结果事件 | 无（事件监听） |

---

## 权限处理

录音需要 `RECORD_AUDIO` 权限。首次使用需调用 `requestAudioPermission()`。

### 方式一：直接调用（推荐）

```js
// 1. 先注册权限结果监听
NativeBridge.onAudioPermissionResult(function (result) {
  if (result.granted) {
    console.log("权限已授予，可以开始录音");
  } else {
    console.log("用户拒绝了麦克风权限");
  }
});

// 2. 请求权限
var res = await NativeBridge.requestAudioPermission();
if (res.data.granted) {
  // 已有权限，直接录音
  await NativeBridge.startRecording();
} else {
  // 等待用户在系统弹窗中授权，结果通过事件回调
}
```

### 方式二：先检查再请求

```js
async function ensurePermission() {
  var state = await NativeBridge.getRecordingState();
  // 如果之前已授权，requestAudioPermission 会直接返回 granted: true
  var res = await NativeBridge.requestAudioPermission();
  return res.data.granted;
}
```

---

## 录音流程

### 标准流程：开始 → 停止

```js
// 开始录音
var startRes = await NativeBridge.startRecording();
console.log(startRes.data.format); // "aac"

// ... 录音中 ...

// 停止录音，获取文件
var stopRes = await NativeBridge.stopRecording();
console.log(stopRes.data.filePath);   // /data/data/.../files/recordings/rec_20260608_143021.m4a
console.log(stopRes.data.duration);   // 毫秒
console.log(stopRes.data.fileSize);   // 字节
```

### 取消录音

```js
await NativeBridge.cancelRecording();
// 录音文件已删除，无需手动清理
```

### 查询录音状态

```js
var state = await NativeBridge.getRecordingState();
console.log(state.data.state);     // "idle" | "recording"
console.log(state.data.filePath);  // 录音中时为当前文件路径
console.log(state.data.duration);  // 录音中时为已录制毫秒数
```

---

## startRecording 参数

```js
NativeBridge.startRecording(options)
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `options.format` | `string` | `"aac"` | 音频格式。支持：`"aac"` (M4A)、`"wav"` |
| `options` | `object` | `{}` | 可选，传空对象或不传均使用默认值 |

### 示例

```js
// 默认 AAC
await NativeBridge.startRecording();

// WAV 格式
await NativeBridge.startRecording({ format: "wav" });
```

---

## stopRecording 返回值

```json
{
  "success": true,
  "data": {
    "filePath": "/data/data/com.example.myapplication/files/recordings/rec_20260608_143021.m4a",
    "duration": 5230,
    "fileSize": 84672,
    "format": "aac"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `filePath` | `string` | 录音文件绝对路径，可用 `file://` 协议在 WebView 中访问 |
| `duration` | `number` | 录音时长（毫秒） |
| `fileSize` | `number` | 文件大小（字节） |
| `format` | `string` | 实际格式：`"aac"` 或 `"wav"` |

### 播放录音

```js
var res = await NativeBridge.stopRecording();
var audio = new Audio("file://" + res.data.filePath);
audio.play();
```

---

## 事件

### audioPermissionResult

权限请求完成后触发（仅在用户通过系统弹窗授权/拒绝后触发）。

```js
window.addEventListener("audioPermissionResult", function (e) {
  console.log(e.detail.granted); // true | false
});

// 或使用便捷方法
NativeBridge.onAudioPermissionResult(function (detail) {
  console.log(detail.granted);
});
```

---

## 错误码

| code | 说明 | 触发场景 |
|------|------|----------|
| `NO_PERMISSION` | 未授权麦克风 | 未调用 `requestAudioPermission` 或用户拒绝 |
| `ALREADY_RECORDING` | 正在录音中 | 重复调用 `startRecording` |
| `NOT_RECORDING` | 当前未在录音 | 调用 `stopRecording` / `cancelRecording` 时未在录音 |
| `START_FAILED` | 启动录音失败 | 系统麦克风被占用或硬件异常 |
| `STOP_FAILED` | 停止录音失败 | MediaRecorder 异常 |
| `INTERNAL_ERROR` | 内部错误 | 文件路径丢失等罕见情况 |

### 错误处理示例

```js
try {
  await NativeBridge.startRecording();
} catch (err) {
  if (err.code === "NO_PERMISSION") {
    // 引导用户授权
    await NativeBridge.requestAudioPermission();
  } else if (err.code === "ALREADY_RECORDING") {
    // 先停止当前录音
    await NativeBridge.stopRecording();
    await NativeBridge.startRecording();
  } else {
    console.error("录音失败:", err.message);
  }
}
```

---

## 完整示例

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>录音 Demo</title>
  <style>
    body { font-family: sans-serif; padding: 24px; }
    button { padding: 12px 24px; margin: 8px; font-size: 16px; border: none; border-radius: 8px; color: #fff; }
    .start { background: #0d6c91; }
    .stop { background: #c0392b; }
    .cancel { background: #7f8c8d; }
    #status { margin: 16px 0; padding: 12px; background: #f0f4f8; border-radius: 8px; }
    audio { width: 100%; margin-top: 12px; }
  </style>
</head>
<body>
  <h2>录音 Demo</h2>
  <div id="status">就绪</div>
  <button class="start" onclick="doStart()">开始录音</button>
  <button class="stop" onclick="doStop()">停止录音</button>
  <button class="cancel" onclick="doCancel()">取消录音</button>
  <div id="player"></div>

  <script src="js/native-bridge.js"></script>
  <script>
    var statusEl = document.getElementById("status");
    var playerEl = document.getElementById("player");
    var timer = null;

    // 监听权限结果
    NativeBridge.onAudioPermissionResult(function (detail) {
      if (detail.granted) {
        statusEl.textContent = "权限已授予，请再次点击开始录音";
      } else {
        statusEl.textContent = "麦克风权限被拒绝";
      }
    });

    async function doStart() {
      try {
        // 检查并请求权限
        var perm = await NativeBridge.requestAudioPermission();
        if (!perm.data.granted) {
          statusEl.textContent = "等待用户授权...";
          return;
        }

        var res = await NativeBridge.startRecording({ format: "aac" });
        statusEl.textContent = "录音中...";
        playerEl.innerHTML = "";

        // 实时显示时长
        var start = Date.now();
        timer = setInterval(function () {
          var sec = Math.floor((Date.now() - start) / 1000);
          statusEl.textContent = "录音中... " + sec + "s";
        }, 500);
      } catch (err) {
        statusEl.textContent = "错误: " + err.message;
      }
    }

    async function doStop() {
      clearInterval(timer);
      try {
        var res = await NativeBridge.stopRecording();
        var d = res.data;
        statusEl.textContent = "已保存: " + d.filePath +
          " (" + Math.round(d.duration / 1000) + "s, " +
          Math.round(d.fileSize / 1024) + "KB)";
        playerEl.innerHTML = '<audio controls src="file://' + d.filePath + '"></audio>';
      } catch (err) {
        statusEl.textContent = "错误: " + err.message;
      }
    }

    async function doCancel() {
      clearInterval(timer);
      try {
        await NativeBridge.cancelRecording();
        statusEl.textContent = "已取消";
        playerEl.innerHTML = "";
      } catch (err) {
        statusEl.textContent = "错误: " + err.message;
      }
    }
  </script>
</body>
</html>
```

---

## 注意事项

1. **权限必须先请求**：首次使用录音前必须调用 `requestAudioPermission()`，否则 `startRecording` 会返回 `NO_PERMISSION` 错误
2. **同时只能录一段**：录音期间再次调用 `startRecording` 会报 `ALREADY_RECORDING`
3. **文件自动清理**：应用退出时自动删除所有录音文件，H5 不需要手动清理
4. **文件路径访问**：WebView 已开启 `allowFileAccess`，可直接用 `file://` 协议访问录音文件
5. **格式选择**：AAC 文件更小、兼容性好；WAV 文件较大但无损，适合需要原始音频数据的场景
