package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MRZ.MRZInfo;
import com.example.myapplication.MRZ.MRZParser;
import com.example.myapplication.Utils.ImageUtils;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import net.sf.scuba.smartcards.CardService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String BROKER_URL = "tcp://192.168.0.132:1883";
    private static final String CLIENT_ID = "AndroidQRandNFCClient_" + System.currentTimeMillis();
    private static final String TOPIC = "access/log";
    private static final String BASE_URL = "https://192.168.0.132:7244/";
    private static final long PROCESSING_DELAY = 1000;
    private static final int INPUT_SIZE = 112;
    private static final int OUTPUT_SIZE = 192;
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;

    private NfcAdapter nfcAdapter;
    private TextView statusTextView;
    private PreviewView previewView;
    private MqttHandler mqttHandler;
    private ExecutorService cameraExecutor;
    private Tag nfcTag;
    private String qrCccdNumber;
    private String qrMrz;
    private Registration qrRegistration;
    private boolean isQrScanned;
    private boolean isNfcRead;
    private boolean isFaceMatched;
    private ImageAnalysis imageAnalysis;
    private CameraSelector cameraSelector;
    private TextRecognizer textRecognizer;
    private FaceDetector faceDetector;
    private Interpreter tfliteInterpreter;
    private Bitmap nfcImageBitmap;
    private float[] nfcFaceEmbedding;
    private long lastProcessTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        isQrScanned = false;
        isNfcRead = false;
        isFaceMatched = false;

        // Initialize ML Kit clients
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        faceDetector = FaceDetection.getClient();

        // Check NFC
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Vui lòng bật NFC", Toast.LENGTH_LONG).show();
        }

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCameraForMrz();
        }

        // Load TensorFlow Lite model
        loadModel();

        mqttHandler = new MqttHandler();
        connectToMqttBroker();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCameraForMrz() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeMrz);

                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                runOnUiThread(() -> statusTextView.setText("Vui lòng quét MRZ trên thẻ CCCD"));
            } catch (Exception e) {
                runOnUiThread(() -> statusTextView.setText("Lỗi camera: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeMrz(ImageProxy imageProxy) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < PROCESSING_DELAY) {
            imageProxy.close();
            return;
        }

        lastProcessTime = currentTime;

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        textRecognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String result = visionText.getText();
                    String mrzStr = filterText(result);
                    if (mrzStr != null) {
                        MRZInfo info = MRZParser.parseMrz(mrzStr);
                        if (info != null) {
                            qrCccdNumber = info.numberCardId9;
                            qrMrz = mrzStr;
                            isQrScanned = true;
                            stopCameraAnalysis();
                            runOnUiThread(() -> {
                                statusTextView.setText("Quét MRZ thành công\nSố CCCD: " + qrCccdNumber + "\nVui lòng áp thẻ CCCD để đọc NFC");
                                Toast.makeText(this, "Áp thẻ CCCD để đọc NFC", Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> statusTextView.setText("Lỗi quét MRZ: " + e.getMessage()));
                    imageProxy.close();
                });
    }

    private String filterText(String text) {
        String[] lines = text.split("\\n");
        List<String> mrzLines = new ArrayList<>();
        for (int i = lines.length - 1; i >= 0 && mrzLines.size() < 3; i--) {
            String line = lines[i].replaceAll(" ", "").replaceAll("«", "<").toUpperCase();
            if (line.contains("<") && line.length() > 25) {
                mrzLines.add(0, line);
            }
        }
        if (mrzLines.size() == 3) {
            return String.join("\n", mrzLines);
        }
        return null;
    }

    private void stopCameraAnalysis() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            Log.d("CAMERA", "ImageAnalysis stopped");
        }
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                cameraProvider.unbindAll();
                Log.d("CAMERA", "Camera unbound");
            } catch (Exception e) {
                Log.e("CAMERA", "Lỗi khi unbind camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCameraForFaceRecognition() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFace);

                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                runOnUiThread(() -> statusTextView.setText("Vui lòng đưa khuôn mặt vào camera"));
            } catch (Exception e) {
                runOnUiThread(() -> statusTextView.setText("Lỗi camera: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFace(ImageProxy imageProxy) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < PROCESSING_DELAY) {
            imageProxy.close();
            return;
        }

        lastProcessTime = currentTime;

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0);
                        Rect boundingBox = face.getBoundingBox();
                        Bitmap bitmap = ImageUtils.mediaImgToBitmap(inputImage.getMediaImage(), inputImage.getRotationDegrees(), boundingBox);
                        Bitmap resizedBitmap = ImageUtils.resizedBitmap(bitmap);
                        float[] currentEmbedding = getFaceEmbedding(resizedBitmap);
                        if (currentEmbedding != null && nfcFaceEmbedding != null) {
                            boolean match = compareFace(nfcFaceEmbedding, currentEmbedding);
                            if (match) {
                                isFaceMatched = true;
                                stopCameraAnalysis();
                                runOnUiThread(() -> {
                                    statusTextView.setText("Khuôn mặt khớp với thẻ CCCD");
                                    Toast.makeText(this, "Khuôn mặt khớp, đang xác minh...", Toast.LENGTH_LONG).show();
                                });
                                verifyCccd(qrCccdNumber);
                            } else {
                                runOnUiThread(() -> statusTextView.setText("Khuôn mặt không khớp với thẻ CCCD"));
                            }
                        }
                    } else {
                        runOnUiThread(() -> statusTextView.setText("Vui lòng đưa khuôn mặt vào camera"));
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> statusTextView.setText("Lỗi nhận diện khuôn mặt: " + e.getMessage()));
                    imageProxy.close();
                });
    }

//    private float[] getFaceEmbedding(Bitmap bitmap) {
//        ByteBuffer imageData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
//        imageData.order(ByteOrder.nativeOrder());
//        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
//        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
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
//        try {
//            tfliteInterpreter.run(inputArray, outputEmbedding);
//            return outputEmbedding[0];
//        } catch (Exception e) {
//            Log.e("FaceRecognition", "Error running TFLite model: " + e.getMessage());
//            return null;
//        }
//    }
    private float[] getFaceEmbedding(Bitmap bitmap) {
        ByteBuffer imageData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);

        imageData.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE*INPUT_SIZE];

        bitmap.getPixels(intValues, 0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());

        imageData.rewind();

        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                imageData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imageData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imageData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        Object[] inputArray = {imageData};
        float[][] outputEmbedding = new float[1][OUTPUT_SIZE];
        Map<Integer,Object> outputMap = new HashMap<>();
        outputMap.put(0,outputEmbedding);
        try {
            tfliteInterpreter.runForMultipleInputsOutputs(inputArray, outputMap);
            return outputEmbedding[0];
        } catch (IllegalArgumentException e) {
            Log.e("FaceRecognition", "Error running TFLite model: " + e.getMessage());
            return null;
        }
    }

    private boolean compareFace(float[] emb1, float[] emb2) {
        float distance = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }
        distance = (float) Math.sqrt(distance);
        return distance < 0.0008f;
    }

    private void loadModel() {
        try {
            String modelFile = "mobile_face_net.tflite";
            tfliteInterpreter = new Interpreter(loadModelFile(modelFile));
        } catch (IOException e) {
            Log.e("FaceRecognition", "Error loading TFLite model: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(String modelFile) throws IOException {
        try (AssetFileDescriptor fileDescriptor = getAssets().openFd(modelFile);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

//    private void readNfcWithMrz(Tag tag, String mrz) {
//        ExecutorService nfcExecutor = Executors.newSingleThreadExecutor();
//        nfcExecutor.execute(() -> {
//            PassportService passportService = null;
//            IsoDep isoDep = null;
//            try {
//                Log.d("NFC", "Bắt đầu đọc NFC cho CCCD, MRZ: " + mrz);
//                isoDep = IsoDep.get(tag);
//                if (isoDep == null) {
//                    throw new IOException("Thẻ không hỗ trợ IsoDep");
//                }
//                isoDep.connect();
//                isoDep.setTimeout(10000);
//
//                CardService cardService = CardService.getInstance(isoDep);
//                passportService = new PassportService(
//                        cardService,
//                        PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH,
//                        PassportService.DEFAULT_MAX_BLOCKSIZE,
//                        false,
//                        false
//                );
//                passportService.open();
//
//                if (mrz == null || mrz.length() < 90) {
//                    throw new IllegalArgumentException("MRZ không hợp lệ");
//                }
//
////                String documentNumber = mrz.substring(5, 14).replace("<", "");
////                String dob = formatDate(mrz.substring(15, 21));
////                String expiry = formatDate(mrz.substring(23, 29));
//                String documentNumberRaw = mrz.substring(18, 27);
//                String documentNumber = documentNumberRaw.replace("<", "");
//                String dob = mrz.substring(31, 37);
//                String expiry = mrz.substring(39, 45);
//                Log.d("NFC", "Tạo BACKey: docNumber=" + documentNumber + ", dob=" + dob + ", expiry=" + expiry);
//
//                BACKey bacKey = new BACKey(documentNumber, dob, expiry);
//                boolean paceSucceeded = false;
//                try {
//                    CardAccessFile cardAccessFile = new CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS));
//                    Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();
//                    for (SecurityInfo securityInfo : securityInfos) {
//                        if (securityInfo instanceof PACEInfo) {
//                            passportService.doPACE(
//                                    bacKey,
//                                    securityInfo.getObjectIdentifier(),
//                                    PACEInfo.toParameterSpec(((PACEInfo) securityInfo).getParameterId()),
//                                    null
//                            );
//                            paceSucceeded = true;
//                            break;
//                        }
//                    }
//                } catch (Exception e) {
//                    Log.w("NFC", "PACE thất bại: " + e.getMessage());
//                }
//
//                passportService.sendSelectApplet(paceSucceeded);
//                if (!paceSucceeded) {
//                    try {
//                        passportService.getInputStream(PassportService.EF_COM).read();
//                    } catch (Exception e) {
//                        passportService.sendSelectApplet(false);
//                        passportService.doBAC(bacKey);
//                    }
//                }
//
//                // Read DG2 for face image
//                InputStream inputStream = passportService.getInputStream(PassportService.EF_DG2);
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                byte[] buffer = new byte[1024];
//                int bytesRead;
//                while ((bytesRead = inputStream.read(buffer)) != -1) {
//                    baos.write(buffer, 0, bytesRead);
//                }
//                inputStream.close();
//                byte[] data = baos.toByteArray();
//
//                ByteArrayInputStream bais = new ByteArrayInputStream(data);
//                DG2File dg2File = new DG2File(bais);
//                bais.close();
//
//                // Extract image (simplified, actual implementation may vary)
//                byte[] imageData = dg2File.getEncoded(); // Adjust based on actual DG2 structure
//                nfcImageBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
//                if (nfcImageBitmap != null) {
//                    analyzeNfcFace();
//                    isNfcRead = true;
//                    runOnUiThread(() -> {
//                        statusTextView.setText("Đọc NFC thành công\nVui lòng đưa khuôn mặt vào camera");
//                        Toast.makeText(this, "Đọc NFC thành công, đưa khuôn mặt vào camera", Toast.LENGTH_LONG).show();
//                    });
//                    startCameraForFaceRecognition();
//                } else {
//                    throw new IOException("Không thể giải mã hình ảnh từ DG2");
//                }
//
//            } catch (Exception e) {
//                Log.e("NFC", "Lỗi đọc NFC: " + e.getMessage(), e);
//                runOnUiThread(() -> {
//                    statusTextView.setText("Lỗi đọc NFC: " + e.getMessage());
//                    Toast.makeText(this, "Lỗi đọc NFC: " + e.getMessage(), Toast.LENGTH_LONG).show();
//                });
//                publishToMqtt(qrCccdNumber, LocalDateTime.now(), "denied");
//            } finally {
//                try {
//                    if (passportService != null) {
//                        passportService.close();
//                    }
//                    if (isoDep != null && isoDep.isConnected()) {
//                        isoDep.close();
//                    }
//                } catch (Exception e) {
//                    Log.e("NFC", "Lỗi khi đóng kết nối: " + e.getMessage());
//                }
//                nfcExecutor.shutdown();
//            }
//        });
//    }
private void readNfcWithMrz(Tag tag, String mrz) {
    ExecutorService nfcExecutor = Executors.newSingleThreadExecutor();
    nfcExecutor.execute(() -> {
        PassportService passportService = null;
        IsoDep isoDep = null;
        InputStream dg2InputStream = null;
        try {
            Log.d("NFC", "Bắt đầu đọc NFC, MRZ: " + mrz);
            isoDep = IsoDep.get(tag);
            if (isoDep == null) {
                throw new IOException("Thẻ không hỗ trợ IsoDep");
            }
            isoDep.connect();
            isoDep.setTimeout(10000);

            CardService cardService = CardService.getInstance(isoDep);
            passportService = new PassportService(
                    cardService,
                    PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    false
            );
            passportService.open();

            if (mrz == null || mrz.length() < 90) {
                throw new IllegalArgumentException("MRZ không hợp lệ, độ dài: " + (mrz != null ? mrz.length() : "null"));
            }

            // Extract fields for BACKey
            String documentNumberRaw = mrz.substring(18,27);
            String documentNumber = documentNumberRaw.replace("<", "");
            String dob = mrz.substring(31, 37);
            String expiry = mrz.substring(39, 45);

            Log.d("NFC", "Tạo BACKey: docNumber=" + documentNumber + ", dob=" + dob + ", expiry=" + expiry);

            BACKey bacKey = new BACKey(documentNumber, dob, expiry);
            boolean paceSucceeded = false;
            try {
                CardAccessFile cardAccessFile = new CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS));
                Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();
                for (SecurityInfo securityInfo : securityInfos) {
                    if (securityInfo instanceof PACEInfo) {
                        passportService.doPACE(
                                bacKey,
                                securityInfo.getObjectIdentifier(),
                                PACEInfo.toParameterSpec(((PACEInfo) securityInfo).getParameterId()),
                                null
                        );
                        paceSucceeded = true;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.w("NFC", "PACE thất bại: " + e.getMessage());
            }

            passportService.sendSelectApplet(paceSucceeded);
            if (!paceSucceeded) {
                try {
                    passportService.getInputStream(PassportService.EF_COM).read();
                } catch (Exception e) {
                    passportService.sendSelectApplet(false);
                    passportService.doBAC(bacKey);
                }
            }

            // Read DG2 for face image
            dg2InputStream = passportService.getInputStream(PassportService.EF_DG2);
            DG2File dg2File = new DG2File(dg2InputStream);
            List<FaceImageInfo> allFaceImageInfo = new ArrayList<>();
            for (FaceInfo faceInfo : dg2File.getFaceInfos()) {
                allFaceImageInfo.addAll(faceInfo.getFaceImageInfos());
            }

            if (allFaceImageInfo.isEmpty()) {
                throw new IOException("Không tìm thấy thông tin hình ảnh khuôn mặt trong DG2");
            }

            // Get the first face image
            FaceImageInfo faceImageInfo = allFaceImageInfo.get(0);
            int imageLength = faceImageInfo.getImageLength();
            DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
            byte[] buffer = new byte[imageLength];
            try {
                dataInputStream.readFully(buffer, 0, imageLength);
                InputStream imageInputStream = new ByteArrayInputStream(buffer, 0, imageLength);
                nfcImageBitmap = BitmapFactory.decodeStream(imageInputStream);
                if (nfcImageBitmap == null) {
                    Log.e("NFC", "Không thể giải mã hình ảnh từ DG2: BitmapFactory trả về null");
                    runOnUiThread(() -> statusTextView.setText("Lỗi: Không thể giải mã hình ảnh từ NFC"));
                    return;
                }
                Log.d("NFC", "Kích thước hình ảnh NFC: " + nfcImageBitmap.getWidth() + "x" + nfcImageBitmap.getHeight());
                Log.d("NFC", "Đọc hình ảnh thành công, kích thước: " + nfcImageBitmap.getWidth() + "x" + nfcImageBitmap.getHeight());
            } catch (IOException e) {
                Log.e("NFC", "Lỗi khi đọc dữ liệu hình ảnh: " + e.getMessage(), e);
                throw new IOException("Lỗi khi đọc dữ liệu hình ảnh từ DG2: " + e.getMessage(), e);
            } finally {
                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        Log.e("NFC", "Lỗi khi đóng DataInputStream: " + e.getMessage());
                    }
                }
            }

            analyzeNfcFace();
            isNfcRead = true;
            runOnUiThread(() -> {
                statusTextView.setText("Đọc NFC thành công\nVui lòng đưa khuôn mặt vào camera");
                Toast.makeText(this, "Đọc NFC thành công, đưa khuôn mặt vào camera", Toast.LENGTH_LONG).show();
            });
            startCameraForFaceRecognition();

        } catch (Exception e) {
            Log.e("NFC", "Lỗi đọc NFC: " + e.getMessage(), e);
            runOnUiThread(() -> {
                statusTextView.setText("Lỗi đọc NFC: " + e.getMessage());
                Toast.makeText(this, "Lỗi đọc NFC: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            publishToMqtt(qrCccdNumber, LocalDateTime.now(), "denied");
        } finally {
            try {
                if (dg2InputStream != null) {
                    dg2InputStream.close();
                }
                if (passportService != null) {
                    passportService.close();
                }
                if (isoDep != null && isoDep.isConnected()) {
                    isoDep.close();
                }
            } catch (Exception e) {
                Log.e("NFC", "Lỗi khi đóng kết nối: " + e.getMessage());
            }
            nfcExecutor.shutdown();
        }
    });
}
//    private void analyzeNfcFace() {
//        InputImage inputImage = InputImage.fromBitmap(nfcImageBitmap, 0);
//        faceDetector.process(inputImage)
//                .addOnSuccessListener(faces -> {
//                    if (faces.size() > 0) {
//                        Face face = faces.get(0);
//                        Rect boundingBox = face.getBoundingBox();
//                        RectF adjustedBoundingBox = new RectF(
//                                boundingBox.left,
//                                boundingBox.top,
//                                boundingBox.right,
//                                boundingBox.bottom
//                        );
//                        Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(nfcImageBitmap, adjustedBoundingBox);
//                        Bitmap resizedFace = ImageUtils.resizedBitmap(croppedFace);
//                        nfcFaceEmbedding = getFaceEmbedding(resizedFace);
//                        if (nfcFaceEmbedding == null) {
//                            runOnUiThread(() -> statusTextView.setText("Không thể trích xuất đặc trưng khuôn mặt từ NFC"));
//                        }
//                    } else {
//                        runOnUiThread(() -> statusTextView.setText("Không tìm thấy khuôn mặt trong ảnh NFC"));
//                    }
//                })
//                .addOnFailureListener(e -> {
//                    runOnUiThread(() -> statusTextView.setText("Lỗi trích xuất khuôn mặt từ NFC: " + e.getMessage()));
//                });
//    }
//private void analyzeNfcFace() {
//    InputImage inputImage = InputImage.fromBitmap(nfcImageBitmap, 0);
//    FaceDetector faceDetector = FaceDetection.getClient();
//
//    faceDetector.process(inputImage)
//            .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
//                @Override
//                public void onSuccess(List<Face> faces) {
//                    Rect boundingBox = null;
//                    if (faces.size() > 0) {
//                        Face face = faces.get(0);
//                        boundingBox = face.getBoundingBox();
//
//                        float padding = 0.0f;
//                        RectF adjustedBoundingBox = new RectF(
//                                boundingBox.left - padding,
//                                boundingBox.top - padding,
//                                boundingBox.right + padding,
//                                boundingBox.bottom + padding
//                        );
//
//                        Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(nfcImageBitmap, adjustedBoundingBox);
//                        Bitmap resizedFace = ImageUtils.resizedBitmap(croppedFace);
//                        nfcFaceEmbedding = getFaceEmbedding(resizedFace);
//
//                        if (nfcFaceEmbedding == null) {
//                            runOnUiThread(() -> statusTextView.setText("Không thể trích xuất đặc trưng khuôn mặt từ NFC"));
//                        }
//                    } else {
//                        runOnUiThread(() -> statusTextView.setText("Không tìm được khuôn mặt trong ảnh NFC"));
//                    }
//                }
//            })
//            .addOnFailureListener(e -> Log.e("NFC", "Face detection failure", e));
//}
//
//    private String formatDate(String date) {
//        if (date == null || date.length() != 6) {
//            return date;
//        }
//        return date.substring(4, 6) + date.substring(2, 4) + date.substring(0, 2);
//    }
private void analyzeNfcFace() {
    Log.d("NFC", "Bắt đầu phân tích khuôn mặt NFC");
    if (nfcImageBitmap == null) {
        Log.e("NFC", "nfcImageBitmap là null");
        runOnUiThread(() -> statusTextView.setText("Lỗi: Hình ảnh NFC là null"));
        return;
    }
    InputImage inputImage = InputImage.fromBitmap(nfcImageBitmap, 0);
    faceDetector.process(inputImage)
            .addOnSuccessListener(faces -> {
                Log.d("NFC", "Số khuôn mặt tìm thấy: " + faces.size());
                if (faces.size() > 0) {
                    Face face = faces.get(0);
                    Rect boundingBox = face.getBoundingBox();
                    Log.d("NFC", "BoundingBox: " + boundingBox.toString());
                    RectF adjustedBoundingBox = new RectF(boundingBox);
                    Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(nfcImageBitmap, adjustedBoundingBox);
                    if (croppedFace == null) {
                        Log.e("NFC", "Không thể cắt ảnh khuôn mặt");
                        runOnUiThread(() -> statusTextView.setText("Lỗi: Không thể cắt ảnh khuôn mặt"));
                        return;
                    }
                    Bitmap resizedFace = ImageUtils.resizedBitmap(croppedFace);
                    if (resizedFace == null) {
                        Log.e("NFC", "Không thể thay đổi kích thước ảnh");
                        runOnUiThread(() -> statusTextView.setText("Lỗi: Không thể thay đổi kích thước ảnh"));
                        return;
                    }
                    nfcFaceEmbedding = getFaceEmbedding(resizedFace);
                    if (nfcFaceEmbedding == null) {
                        Log.e("NFC", "Không thể trích xuất đặc trưng khuôn mặt");
                        runOnUiThread(() -> statusTextView.setText("Không thể trích xuất đặc trưng khuôn mặt từ NFC"));
                    } else {
                        Log.d("NFC", "Trích xuất đặc trưng thành công, kích thước: " + nfcFaceEmbedding.length);
                    }
                } else {
                    Log.e("NFC", "Không tìm thấy khuôn mặt trong ảnh NFC");
                    runOnUiThread(() -> statusTextView.setText("Không tìm thấy khuôn mặt trong ảnh NFC"));
                }
            })
            .addOnFailureListener(e -> {
                Log.e("NFC", "Lỗi phát hiện khuôn mặt: " + e.getMessage(), e);
                runOnUiThread(() -> statusTextView.setText("Lỗi trích xuất khuôn mặt từ NFC: " + e.getMessage()));
            });
}
    private void connectToMqttBroker() {
        try {
            mqttHandler.connect(BROKER_URL, CLIENT_ID);
            Log.d("MQTT", "Kết nối MQTT thành công");
            runOnUiThread(() -> statusTextView.setText("Kết nối MQTT thành công\nVui lòng quét MRZ"));
        } catch (Exception e) {
            Log.e("MQTT", "Lỗi kết nối MQTT: " + e.getMessage());
            runOnUiThread(() -> statusTextView.setText("Lỗi kết nối MQTT: " + e.getMessage()));
        }
    }

    private void publishToMqtt(String cccdNumber, LocalDateTime accessTime, String result) {
        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("cccdId", cccdNumber != null ? cccdNumber : "unknown");
            messageJson.put("accessTime", formatDateTime(accessTime));
            messageJson.put("status", result.equalsIgnoreCase("allowed") ? 1 : 0);

            mqttHandler.publish(TOPIC, messageJson.toString());
            Log.d("MQTT", "Gửi MQTT thành công");
        } catch (Exception e) {
            Log.e("MQTT", "Lỗi gửi MQTT: " + e.getMessage());
            runOnUiThread(() -> statusTextView.setText("Lỗi gửi MQTT: " + e.getMessage()));
            connectToMqttBroker();
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return dateTime.format(formatter);
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isProcessingApi = false;

    private void verifyCccd(String cccdNumber) {
        if (!isFaceMatched || isProcessingApi) {
            Log.d("verifyCccd", "Khuôn mặt chưa khớp hoặc đang xử lý API");
            return;
        }
        isProcessingApi = true;

        OkHttpClient client = getUnsafeOkHttpClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<RegistrationResponse> call = apiService.verifyCccd(cccdNumber);

        call.enqueue(new Callback<RegistrationResponse>() {
            @Override
            public void onResponse(Call<RegistrationResponse> call, Response<RegistrationResponse> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        RegistrationResponse regResponse = response.body();
                        if (regResponse.getErrCode() == 200 && regResponse.getData() != null && !regResponse.getData().isEmpty()) {
                            qrRegistration = regResponse.getData().get(0);
                            StringBuilder registrationInfo = new StringBuilder("Xác minh thành công\n");
                            registrationInfo.append("MRZ: ").append(qrRegistration.getMrz() != null ? qrRegistration.getMrz() : "Không xác định").append("\n");
                            registrationInfo.append("Mục đích: ").append(qrRegistration.getPurpose() != null ? qrRegistration.getPurpose() : "Không xác định").append("\n");
                            registrationInfo.append("Thời gian bắt đầu: ").append(qrRegistration.getStartTime() != null ? qrRegistration.getStartTime() : "N/A").append("\n");
                            registrationInfo.append("Thời gian kết thúc: ").append(qrRegistration.getEndTime() != null ? qrRegistration.getEndTime() : "N/A").append("\n");
                            registrationInfo.append("ID: ").append(qrRegistration.getId());

                            LocalDateTime now = LocalDateTime.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                            LocalDateTime startTime = LocalDateTime.parse(qrRegistration.getStartTime(), formatter);
                            LocalDateTime endTime = LocalDateTime.parse(qrRegistration.getEndTime(), formatter);

                            if (qrRegistration.getStatus() == 1 && now.isAfter(startTime) && now.isBefore(endTime)) {
                                final String successMessage = registrationInfo.toString();
                                final String purpose = qrRegistration.getPurpose();
                                runOnUiThread(() -> {
                                    statusTextView.setText(successMessage);
                                    Toast.makeText(MainActivity.this, "Xác minh hợp lệ: " + purpose, Toast.LENGTH_LONG).show();
                                });
                                publishToMqtt(cccdNumber, now, "allowed");
                                openDoor();
                            } else {
                                final String reason = qrRegistration.getStatus() != 1 ? "Chưa được duyệt" : "Ngoài thời gian cho phép";
                                runOnUiThread(() -> statusTextView.setText("Xác minh thất bại: " + reason));
                                publishToMqtt(cccdNumber, now, "denied");
                            }
                        } else {
                            StringBuilder errorBuilder = new StringBuilder("Bạn chưa đăng ký\nServer trả về: ");
                            errorBuilder.append(regResponse.getErrDesc() != null ? regResponse.getErrDesc() : "Không có dữ liệu");
                            final String errorMessage = errorBuilder.toString();
                            runOnUiThread(() -> statusTextView.setText(errorMessage));
                            publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                        }
                    } else {
                        StringBuilder errorBuilder = new StringBuilder("Lỗi server: Bạn chưa đăng ký");
                        if (response.errorBody() != null) {
                            try {
                                errorBuilder.append("\nServer trả về: ").append(response.errorBody().string());
                            } catch (IOException e) {
                                errorBuilder.append("\n(Lỗi khi đọc body lỗi: ").append(e.getMessage()).append(")");
                            }
                        }
                        final String errorMessage = errorBuilder.toString();
                        runOnUiThread(() -> statusTextView.setText(errorMessage));
                        publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                    }
                } finally {
                    isProcessingApi = false;
                    resetState();
                }
            }

            @Override
            public void onFailure(Call<RegistrationResponse> call, Throwable t) {
                final String errorMessage = "Lỗi kết nối server: " + t.getMessage();
                runOnUiThread(() -> statusTextView.setText(errorMessage));
                publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                isProcessingApi = false;
                resetState();
            }
        });
    }

    private void handleNfcIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (nfcTag != null && isQrScanned && qrMrz != null) {
                runOnUiThread(() -> statusTextView.setText("Đang đọc thẻ NFC..."));
                readNfcWithMrz(nfcTag, qrMrz);
            } else {
                runOnUiThread(() -> {
                    statusTextView.setText("Vui lòng quét MRZ trước");
                    Toast.makeText(this, "Quét MRZ trước khi áp thẻ NFC", Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    private void resetState() {
        qrCccdNumber = null;
        qrMrz = null;
        qrRegistration = null;
        isQrScanned = false;
        isNfcRead = false;
        isFaceMatched = false;
        nfcImageBitmap = null;
        nfcFaceEmbedding = null;
        nfcTag = null;
        runOnUiThread(() -> statusTextView.setText("Vui lòng quét MRZ"));
        startCameraForMrz();
    }

    private void openDoor() {
        runOnUiThread(() -> Toast.makeText(this, "Cửa mở", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isQrScanned) {
            handleNfcIntent(intent);
        } else {
            Toast.makeText(this, "Vui lòng quét MRZ trước", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            IntentFilter[] intentFilters = new IntentFilter[]{
                    new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            };
            String[][] techList = new String[][]{new String[]{"android.nfc.tech.IsoDep"}};
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techList);
        } else {
            Toast.makeText(this, "Vui lòng bật NFC", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraForMrz();
        } else {
            statusTextView.setText("Cần quyền camera để quét MRZ");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttHandler != null) {
            mqttHandler.disconnect();
        }
        cameraExecutor.shutdown();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
    }
}