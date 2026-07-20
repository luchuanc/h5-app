(function () {
  var tapTimes = [];
  var target = document.getElementById("heroTapTarget");

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
          console.error(error);
        }
      }
    }
  }

  target.addEventListener("click", registerTap);
})();
