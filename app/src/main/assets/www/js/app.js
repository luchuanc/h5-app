document.addEventListener("DOMContentLoaded", function () {
  var result = document.getElementById("result");
  var recResult = document.getElementById("rec-result");
  var recStatus = document.getElementById("rec-status");
  var recPlayer = document.getElementById("rec-player");

  function print(value) {
    result.textContent = typeof value === "string"
      ? value
      : JSON.stringify(value, null, 2);
  }

  function recPrint(value) {
    recResult.textContent = typeof value === "string"
      ? value
      : JSON.stringify(value, null, 2);
  }

  var timer = null;
  var recordStart = 0;

  function setRecStatus(text) {
    recStatus.textContent = "录音状态：" + text;
  }

  NativeBridge.onAudioPermissionResult(function (detail) {
    recPrint({ event: "audioPermissionResult", detail: detail });
    setRecStatus(detail.granted ? "权限已授予" : "权限被拒绝");
  });

  async function recRun(action) {
    try {
      switch (action) {
        case "reqPerm":
          recPrint(await NativeBridge.requestAudioPermission());
          break;
        case "recState":
          recPrint(await NativeBridge.getRecordingState());
          break;
        case "recStart":
          recPrint(await NativeBridge.startRecording({ format: "aac" }));
          recordStart = Date.now();
          recPlayer.innerHTML = "";
          timer = setInterval(function () {
            var sec = Math.floor((Date.now() - recordStart) / 1000);
            setRecStatus("录音中... " + sec + "s");
          }, 500);
          break;
        case "recStop":
          clearInterval(timer);
          var res = await NativeBridge.stopRecording();
          recPrint(res);
          var d = res.data;
          setRecStatus("已保存 " + Math.round(d.duration / 1000) + "s, " +
            Math.round(d.fileSize / 1024) + "KB");
          recPlayer.innerHTML =
            '<audio controls src="file://' + d.filePath + '" style="width:100%"></audio>';
          break;
        case "recCancel":
          clearInterval(timer);
          recPrint(await NativeBridge.cancelRecording());
          setRecStatus("已取消");
          recPlayer.innerHTML = "";
          break;
        default:
          recPrint({ success: false, message: "Unknown action: " + action });
      }
    } catch (err) {
      clearInterval(timer);
      recPrint(err);
      setRecStatus("错误：" + (err.message || err.code || JSON.stringify(err)));
    }
  }

  async function run(action) {
    try {
      switch (action) {
        case "runtime":
          print(await window.NativeBridge.getRuntimeInfo());
          break;
        case "device":
          print(await window.NativeBridge.getDeviceInfo());
          break;
        case "package":
          print(await window.NativeBridge.getPackageInfo());
          break;
        case "bundlePicker":
          print(await window.NativeBridge.openBundlePicker());
          break;
        case "toast":
          print(await window.NativeBridge.toast("容器侧 Toast 调用成功"));
          break;
        case "external":
          print(await window.NativeBridge.openExternalUrl("https://developer.android.com/"));
          break;
        case "close":
          print(await window.NativeBridge.closeApp());
          break;
        default:
          print({
            success: false,
            code: "UNKNOWN_ACTION",
            message: "Unknown action: " + action
          });
      }
    } catch (error) {
      print(error);
    }
  }

  document.querySelectorAll("[data-action]").forEach(function (button) {
    button.addEventListener("click", function () {
      var action = button.getAttribute("data-action");
      if (action.startsWith("rec") || action === "reqPerm") {
        recRun(action);
      } else {
        run(action);
      }
    });
  });

  run("runtime");
});
