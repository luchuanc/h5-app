(function () {
  var tapTimes = [];
  var target = document.getElementById("heroTapTarget");
  var result = document.getElementById("result");

  function print(value) {
    result.textContent = typeof value === "string"
      ? value
      : JSON.stringify(value, null, 2);
  }

  function registerTap() {
    var now = Date.now();
    tapTimes.push(now);
    tapTimes = tapTimes.filter(function (time) {
      return now - time <= 3000;
    });

    if (tapTimes.length >= 10) {
      tapTimes = [];
      if (window.NativeBridgeHost && window.NativeBridgeHost.openBundlePicker) {
        try {
          window.NativeBridgeHost.openBundlePicker();
        } catch (error) {
          print(error);
        }
      }
    }
  }

  async function run(action) {
    try {
      switch (action) {
        case "runtime":
          print(JSON.parse(window.NativeBridgeHost.getRuntimeInfo()));
          break;
        case "device":
          print(JSON.parse(window.NativeBridgeHost.getDeviceInfo()));
          break;
        case "package":
          print(JSON.parse(window.NativeBridgeHost.getPackageInfo()));
          break;
        case "bundles":
          print(JSON.parse(window.NativeBridgeHost.getAvailableBundles()));
          break;
        case "picker":
          print(JSON.parse(window.NativeBridgeHost.openBundlePicker()));
          break;
        case "toast":
          print(JSON.parse(window.NativeBridgeHost.toast("Runtime Lab toast ok")));
          break;
        case "external":
          print(JSON.parse(window.NativeBridgeHost.openExternalUrl("https://developer.android.com/")));
          break;
        case "close":
          print(JSON.parse(window.NativeBridgeHost.closeApp()));
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

  target.addEventListener("click", registerTap);

  document.querySelectorAll("[data-action]").forEach(function (button) {
    button.addEventListener("click", function () {
      run(button.getAttribute("data-action"));
    });
  });

  run("runtime");
})();
