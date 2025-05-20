//package com.example.myapplication;
//
//import android.Manifest;
//import android.app.Activity;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.content.res.AssetFileDescriptor;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Rect;
//import android.graphics.RectF;
//import android.media.Image;
//import android.os.Bundle;
//import android.util.Base64;
//import android.util.Log;
//import android.util.Size;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.activity.EdgeToEdge;
//import androidx.annotation.NonNull;
//import androidx.annotation.OptIn;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.ExperimentalGetImage;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageProxy;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.view.PreviewView;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.graphics.Insets;
//import androidx.core.view.ViewCompat;
//import androidx.core.view.WindowInsetsCompat;
//
//import com.example.myapplication.Utils.ImageUtils;
//import com.google.android.gms.tasks.OnSuccessListener;
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.face.Face;
//import com.google.mlkit.vision.face.FaceDetection;
//import com.google.mlkit.vision.face.FaceDetector;
//
//
//import org.tensorflow.lite.Interpreter;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//
//public class FaceRecognition extends AppCompatActivity {
//    private long lastProcessTime = 0;
//    private static final long PROCESSING_DELAY = 1000;
//
//    final String TAG = "FaceRecognition";
//    private Interpreter tfliteInterpreter;
//
//    private static final int INPUT_SIZE = 112;
//    private static final int OUTPUT_SIZE=192;
//    private static final float IMAGE_MEAN = 128.0f;
//    private static final float IMAGE_STD = 128.0f;
//
//
//    private TextView detectionTextView;
//    private PreviewView previewView;
//    private Preview previewUseCase;
//    private ImageAnalysis analysisUseCase;
//    private final int CAMERA_PERMISSION_REQUEST = 1001;
//    private ProcessCameraProvider cameraProvider;
//
//    private Executor executor;
//
//    private CameraSelector cameraSelector;
//    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
//
//    private Bitmap decodedBitmap;
//
//    private String imageBase64;
//
//    private float[] comparedFaceEmbedding = null;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_face_recognition);
//
//        imageBase64 = getIntent().getStringExtra("ImageBase64");
//        byte[] decodeBytes = Base64.decode(imageBase64,Base64.DEFAULT);
//        decodedBitmap = BitmapFactory.decodeByteArray(decodeBytes,0,decodeBytes.length);
//
//        detectionTextView = findViewById(R.id.detectionTextView);
//        previewView = findViewById(R.id.previewViewFront);
//        executor = ContextCompat.getMainExecutor(this);
//
//        // Yêu cầu quyền truy cập camera từ người dùng
//        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION_REQUEST);
//        } else {
//            startCamera();
//        }
//
//        loadModel();
//
//        analyzeFaceFromCard();
//    }
//
//    private void analyzeFaceFromCard() {
//        InputImage inputImage = InputImage.fromBitmap(decodedBitmap,0);
//        FaceDetector faceDetector = FaceDetection.getClient();
//
//        faceDetector.process(inputImage)
//                .addOnSuccessListener(
//                        new OnSuccessListener<List<Face>>() {
//                            @Override
//                            public void onSuccess(List<Face> faces) {
//                                Rect boundingBox = null;
//                                if (faces.size() > 0) {
//
//                                    Face face = faces.get(0);
//
//                                    boundingBox = face.getBoundingBox();
//
//                                    float padding = 0.0f;
//
//                                    RectF adjustedBoundingBox = new RectF(
//                                            boundingBox.left - padding,
//                                            boundingBox.top - padding,
//                                            boundingBox.right + padding,
//                                            boundingBox.bottom + padding);
//
//                                    Bitmap cropped_face = ImageUtils.getCropBitmapByCPU(decodedBitmap, adjustedBoundingBox);
//
//                                    Bitmap bitmap = ImageUtils.resizedBitmap(cropped_face);
//
//
//                                    comparedFaceEmbedding = getFaceEmbedding(bitmap);
//                                } else {
//                                    runOnUiThread(() -> {
//                                        detectionTextView.setText("Không tìm được khuôn mặt trong ảnh cccd");
//                                    });
//                                }
//                            }
//                        }
//                )
//                .addOnFailureListener(e -> Log.e(TAG, "Barcode process failure", e));
//
//    }
//
//    // Hàm xử lý khi người dùng chấp nhận hay từ chối cấp quyền cho camera
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
//        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startCamera();
//        } else {
//            Toast.makeText(this,"Camera permission required!",Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
//    private void startCamera() {
//        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
//        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
//        cameraProviderFuture.addListener(() -> {
//            try {
//                cameraProvider = cameraProviderFuture.get();
//                bindAllCameraUseCases();
//            } catch (ExecutionException | InterruptedException e) {
//                Log.e(TAG, "cameraProviderFuture.addListener Error", e);
//            }
//        },executor);
//    }
//
//    private void bindAllCameraUseCases() {
//        if (cameraProvider != null) {
//            cameraProvider.unbindAll();
//            bindPreviewUseCase();
//            bindAnalysisUseCase();
//        }
//    }
//
//    private void bindPreviewUseCase() {
//        if (cameraProvider == null) {
//            return;
//        }
//
//        if (previewUseCase != null) {
//            cameraProvider.unbind(previewUseCase);
//        }
//
//        Preview.Builder builder  = new Preview.Builder();
//
//        previewUseCase = builder.build();
//        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
//
//        try {
//            cameraProvider.bindToLifecycle(this,cameraSelector,previewUseCase);
//        } catch (Exception e) {
//            Log.e(TAG, "Error when bind preview", e);
//        }
//    }
//
//    private void bindAnalysisUseCase() {
//        if (cameraProvider == null) {
//            return;
//        }
//
//        if (analysisUseCase != null) {
//            cameraProvider.unbind(analysisUseCase);
//        }
//
//        Executor camereExecutor = Executors.newSingleThreadExecutor();
//
//        ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
//
//        analysisUseCase = builder.build();
//        analysisUseCase.setAnalyzer(camereExecutor,imageProxy -> {
//            analyze(imageProxy);
//        });
//
//        try {
//            cameraProvider.bindToLifecycle(this,cameraSelector,analysisUseCase);
//        } catch (Exception e) {
//            Log.e(TAG, "Error when bind analysis", e);
//        }
//    }
//
//    @OptIn(markerClass = ExperimentalGetImage.class)
//    private void analyze(ImageProxy imageProxy) {
//        long currentTime = System.currentTimeMillis();
//        if (currentTime - lastProcessTime < PROCESSING_DELAY) {
//            imageProxy.close();
//            return;
//        }
//
//        lastProcessTime = currentTime;
//        if (imageProxy.getImage() == null) {
//            return;
//        }
//
//        InputImage inputImage = InputImage.fromMediaImage(
//                imageProxy.getImage(),
//                imageProxy.getImageInfo().getRotationDegrees()
//        );
//
//        FaceDetector faceDetector = FaceDetection.getClient();
//
//        faceDetector.process(inputImage)
//                .addOnSuccessListener(faces -> onSuccessListener(faces,inputImage))
//                .addOnFailureListener(e -> Log.e(TAG,"Barcode process failer",e))
//                .addOnCompleteListener(task -> imageProxy.close());
//
//    }
//
//    private void onSuccessListener(List<Face> faces, InputImage inputImage) {
//        Rect boundingBox = null;
//
//        if (faces.size() > 0) {
//            Face face = faces.get(0);
//
//            boundingBox = face.getBoundingBox();
//
//            Bitmap bitmap = ImageUtils.mediaImgToBitmap(
//                    inputImage.getMediaImage(),
//                    inputImage.getRotationDegrees(),
//                    boundingBox);
//
//            float[] currentEmbedding = getFaceEmbedding(bitmap);
//            if (currentEmbedding != null) {
//                if (comparedFaceEmbedding != null) {
//                    boolean match = compareFace(comparedFaceEmbedding,currentEmbedding);
//                    if (match) {
//                        runOnUiThread(() -> {
//                            detectionTextView.setText("Face matches citizen ID");
//                            Intent intent = new Intent(this, UpdateLocation.class);
//                            startActivity(intent);
//                        });
//
//                    } else {
//                        Log.d(TAG,"FACE not match");
//                        runOnUiThread(() -> {
//                            detectionTextView.setText("Face doesn't match citizen ID");
//                        });
//
//
//                    }
//                }
//            }
//
//        } else {
//            runOnUiThread(() -> {
//                detectionTextView.setText("Bring your face close to camera");
//            });
//        }
//    }
//
//    private float[] getFaceEmbedding(Bitmap bitmap) {
//        ByteBuffer imageData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
//
//        imageData.order(ByteOrder.nativeOrder());
//
//        int[] intValues = new int[INPUT_SIZE*INPUT_SIZE];
//
//        bitmap.getPixels(intValues, 0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
//
//        imageData.rewind();
//
//        for (int i = 0; i < INPUT_SIZE; ++i) {
//            for (int j = 0; j < INPUT_SIZE; ++j) {
//                int pixelValue = intValues[i * INPUT_SIZE + j];
//                imageData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//                imageData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//                imageData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//            }
//        }
//
//        Object[] inputArray = {imageData};
//        float[][] outputEmbedding = new float[1][OUTPUT_SIZE];
//        Map<Integer,Object> outputMap = new HashMap<>();
//        outputMap.put(0,outputEmbedding);
//        try {
//            tfliteInterpreter.runForMultipleInputsOutputs(inputArray, outputMap);
//            return outputEmbedding[0];
//        } catch (IllegalArgumentException e) {
//            Log.e(TAG, "Error running TFLite model: " + e.getMessage());
//            return null;
//        }
//    }
//
//    private boolean compareFace(float[] emb1,float[] emb2) {
//        float distance = 0;
//        for (int i=0;i<emb1.length;i++) {
//            float diff = emb1[i] - emb2[i];
//            distance = diff*diff;
//        }
//
//        distance = (float) Math.sqrt(distance);
//        if (distance < 0.0008f) {
//            return true;
//        } else {
//            return false;
//        }
//    }
//
//    private void loadModel() {
//        try {
//            //model name
//            String modelFile = "mobile_face_net.tflite";
//            tfliteInterpreter = new Interpreter(loadModelFile(FaceRecognition.this, modelFile));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
//        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = fileDescriptor.getStartOffset();
//        long declaredLength = fileDescriptor.getDeclaredLength();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//    }
//}