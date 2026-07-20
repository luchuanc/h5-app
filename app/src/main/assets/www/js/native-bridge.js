(function () {
  function ensureHost() {
    if (!window.NativeBridgeHost) {
      throw {
        success: false,
        code: "BRIDGE_NOT_READY",
        message: "Native bridge host is not ready"
      };
    }
  }

  function invoke(method, args) {
    return Promise.resolve().then(function () {
      ensureHost();
      var raw = window.NativeBridgeHost[method].apply(window.NativeBridgeHost, args || []);
      var result = typeof raw === "string" ? JSON.parse(raw) : raw;

      if (!result || result.success !== true) {
        throw result || {
          success: false,
          code: "UNKNOWN_NATIVE_ERROR",
          message: "Native call failed"
        };
      }

      return result;
    });
  }

  window.NativeBridge = {
    getRuntimeInfo: function () {
      return invoke("getRuntimeInfo");
    },
    getDeviceInfo: function () {
      return invoke("getDeviceInfo");
    },
    getPackageInfo: function () {
      return invoke("getPackageInfo");
    },
    openBundlePicker: function () {
      return invoke("openBundlePicker");
    },
    toast: function (message) {
      return invoke("toast", [String(message || "")]);
    },
    closeApp: function () {
      return invoke("closeApp");
    },
    openExternalUrl: function (url) {
      return invoke("openExternalUrl", [String(url || "")]);
    },

    // ── Recording APIs ──────────────────────────────────────────
    requestAudioPermission: function () {
      return invoke("requestAudioPermission");
    },
    startRecording: function (options) {
      return invoke("startRecording", [JSON.stringify(options || {})]);
    },
    stopRecording: function () {
      return invoke("stopRecording");
    },
    cancelRecording: function () {
      return invoke("cancelRecording");
    },
    getRecordingState: function () {
      return invoke("getRecordingState");
    },
    readRecordingFile: function (filePath) {
      return invoke("readRecordingFile", [String(filePath || "")]);
    },
    onAudioPermissionResult: function (callback) {
      window.addEventListener("audioPermissionResult", function (e) {
        callback(e.detail);
      });
    }
  };
})();
