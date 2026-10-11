# Bug report draft for KonstaKANG / Raspberry Vanilla (v4l2_codec2 encoder on Pi 4)

Title: `c2.v4l2.avc.encoder` fails with Surface input (Android Auto, screen capture): "lock() ... not allocated with a BufferUsage::CPU_* usage"

Build: LineageOS 23.2 20260520 (rpi4), Android Auto 17.9 as the consumer, but any app using `MediaCodec.createInputSurface()` fed by a GPU surface hits it.

Symptoms (logcat):
```
android.hardware.media.c2-service-v4l2: Attempted to lock() a buffer that was not allocated with a BufferUsage::CPU_* usage.
Gralloc5: lock(..., ...) failed: 3
C2AllocatorGralloc: failed transaction: lock(RGBA_8888)
V4L2EncodeComponent: Failed to convert input block (index: 0)
MediaCodec: Codec reported err 0x80000000/UNKNOWN_ERROR, actionCode 0, while in state 6/STARTED
```
Cause: `V4L2EncodeInterface::Initialize()` has the `C2_PARAMKEY_INPUT_STREAM_USAGE` parameter commented out
(external/v4l2_codec2/components/V4L2EncodeInterface.cpp, "TODO(b/167640667)"). CCodec therefore reports
`android._C2MemoryUsage = 0` and the producer allocates GPU-only buffers, but the format convertor then maps the block on the CPU.

Fix (tested, built from raspberry-vanilla/android_external_v4l2_codec2 android-16.0, hardware encoding then works): see docs/v4l2-encoder-cpu-read.patch,
i.e. enable the parameter with `C2MemoryUsage::CPU_READ`.

Observation after the fix (720p and 800x480, YouTube playback through Android Auto's virtual display): encoding works and the
service uses ~40% of a core, but the delivered frame rate is still only ~13-14 fps at both sizes, so the next bottleneck is
probably the CPU RGBA->NV12 conversion / uncached GPU buffer reads or the GPU composition path.
