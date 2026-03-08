# sherpa-onnx model assets

Put your sherpa-onnx streaming model files in this folder.

Expected path root:
- `app/src/main/assets/sherpa-onnx/`

This project initializes sherpa-onnx using the official helper `getModelConfig(type=0, assetManager)`.
You should copy the same model layout used by the official SherpaOnnx Android example.

If model files are missing, the app will show:
- "缺少 sherpa-onnx 模型文件，请先放入 assets"
