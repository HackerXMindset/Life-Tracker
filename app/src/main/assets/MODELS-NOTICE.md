# Face models

Used only for the optional Face scan lock. Everything runs on the phone.

- `facenet.tflite`: FaceNet face embeddings (128 numbers per face), as packaged in shubham0204/OnDevice-Face-Recognition-Android (Apache-2.0).
- `spoof_model_scale_2_7.tflite`, `spoof_model_scale_4_0.tflite`: MiniFASNet liveness (is this a real face or a picture), from minivision-ai/Silent-Face-Anti-Spoofing (Apache-2.0), converted to TFLite as packaged in shubham0204/FaceRecognition_With_FaceNet_Android (Apache-2.0).
