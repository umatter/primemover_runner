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

  // The pause/mute/src-strip/load teardown that used to run here (3s after
  // every navigation, to free decoder buffers on the GPU-less 8-core Hetzner
  // VM) was removed on 2026-08-24. Measured on the production stack before
  // removal: it killed playback for ~15-20s after EVERY navigation before
  // YouTube's player self-recovered, cutting actual watch time to roughly a
  // third of the scripted dwell. On this host (62 GB RAM, Arrow Lake iGPU with
  // VA-API hardware decode enabled 2026-08-22) the memory pressure it guarded
  // against does not exist. See docs/chrome-browser-death-fix.txt in the
  // orchestrator repo for the measurement.

  window.__h264OverrideApplied = true;
})();
