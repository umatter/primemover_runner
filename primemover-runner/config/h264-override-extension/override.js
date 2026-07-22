(function() {
  if (window.__h264OverrideApplied) return;

  // Force H.264 instead of VP9/AV1 (lighter on CPU for software decoding)
  var origIsTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);
  MediaSource.isTypeSupported = function(mimeType) {
    if (mimeType && (mimeType.indexOf('vp9') !== -1 || mimeType.indexOf('vp09') !== -1 || mimeType.indexOf('av01') !== -1)) {
      return false;
    }
    return origIsTypeSupported(mimeType);
  };
  MediaSource.isTypeSupported.toString = function() { return 'function isTypeSupported() { [native code] }'; };
  Object.defineProperty(MediaSource.isTypeSupported, 'name', { value: 'isTypeSupported', configurable: true });

  // Pause and mute video after YouTube's player initializes on each navigation.
  // This frees media decoder buffers that otherwise grow to 1GB+.
  // Uses YouTube's SPA navigation event (yt-navigate-finish) to catch all page loads.
  function pauseVideo() {
    var video = document.querySelector('video');
    if (video) {
      video.pause();
      video.muted = true;
      // Remove buffered data by resetting src to free decoder memory
      video.removeAttribute('src');
      video.load();
    }
  }

  // YouTube fires yt-navigate-finish on every SPA navigation
  document.addEventListener('yt-navigate-finish', function() {
    // Delay slightly to let the player initialize, then kill it
    setTimeout(pauseVideo, 3000);
  });

  // Also handle initial page load
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      setTimeout(pauseVideo, 3000);
    });
  } else {
    setTimeout(pauseVideo, 3000);
  }

  window.__h264OverrideApplied = true;
})();
