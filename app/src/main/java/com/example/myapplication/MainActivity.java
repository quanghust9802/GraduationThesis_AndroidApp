package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG1File;
import org.json.JSONObject;

import net.sf.scuba.smartcards.CardService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
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

    private static final String BROKER_URL = "tcp://192.168.0.117:1883";
    private static final String CLIENT_ID = "AndroidQRandNFCClient_" + System.currentTimeMillis();
    private static final String TOPIC = "access/log";
    private static final String BASE_URL = "https://192.168.0.117:7244/";

    private NfcAdapter nfcAdapter;
    private TextView statusTextView;
    private PreviewView previewView;
    private MqttHandler mqttHandler; // Thay MqttAndroidClient bằng MqttHandler
    private ExecutorService cameraExecutor;
    private Tag nfcTag;
    private String qrCccdNumber;
    private String qrMrz;
    private Registration qrRegistration;
    private boolean isQrScanned;
    private ImageAnalysis imageAnalysis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        isQrScanned = false;

        // Check NFC
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Vui lòng bật NFC", Toast.LENGTH_LONG).show();
        }

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }

        // Khởi tạo MqttHandler và kết nối
        mqttHandler = new MqttHandler();
        connectToMqttBroker();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (imageProxy.getImage() != null) {
                        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
                        BarcodeScanner scanner = BarcodeScanning.getClient();
                        scanner.process(image)
                                .addOnSuccessListener(barcodes -> {
                                    for (Barcode barcode : barcodes) {
                                        if (barcode.getFormat() == Barcode.FORMAT_QR_CODE) {
                                            String qrData = barcode.getRawValue();
                                            if (qrData != null) {
                                                processQrData(qrData);
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    runOnUiThread(() -> statusTextView.setText("Lỗi quét QR: " + e.getMessage()));
                                })
                                .addOnCompleteListener(task -> imageProxy.close());
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                runOnUiThread(() -> statusTextView.setText("Lỗi camera: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCameraAnalysis() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            Log.d("NFC_DEBUG", "ImageAnalysis stopped");
        } else {
            Log.e("NFC_DEBUG", "ImageAnalysis is null");
        }
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                cameraProvider.unbindAll();
                Log.d("NFC_DEBUG", "Camera unbound");
            } catch (Exception e) {
                Log.e("NFC_DEBUG", "Lỗi khi unbind camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processQrData(String qrData) {
        if (isQrScanned) {
            runOnUiThread(() -> statusTextView.setText("QR đã được quét, vui lòng bỏ qua"));
            Log.d("QR", "QR đã được quét, bỏ qua");
            return;
        }
        String[] parts = qrData.split("\\|");
        for (int i = 0; i < parts.length; i++) {
            Log.d("NFC", "Phần tử " + i + ": " + parts[i]);
        }
        if (parts.length >= 1 && isValidCccdId(parts[0])) {
            qrCccdNumber = parts[0];
            StringBuilder displayTextBuilder = new StringBuilder("Quét mã QR thành công\n");
            displayTextBuilder.append("Số CCCD: ").append(qrCccdNumber);
            if (parts.length >= 2) {
                displayTextBuilder.append("\nSố CMND: ").append(parts[1]);
            }
            if (parts.length >= 3) {
                displayTextBuilder.append("\nHọ và tên: ").append(parts[2]);
            }
            if (parts.length >= 4) {
                displayTextBuilder.append("\nNgày sinh: ").append(parts[3]);
            }
            if (parts.length >= 5) {
                displayTextBuilder.append("\nGiới tính: ").append(parts[4]);
            }

            String finalDisplayText = displayTextBuilder.toString();
            runOnUiThread(() -> statusTextView.setText(finalDisplayText));

            verifyCccd(qrCccdNumber);
            stopCameraAnalysis();
        } else {
            runOnUiThread(() -> statusTextView.setText("Dữ liệu QR không hợp lệ"));
            resetState();
        }
    }

    private void connectToMqttBroker() {
        try {
            // Gọi hàm connect của MqttHandler
            mqttHandler.connect(BROKER_URL, CLIENT_ID);
            Log.d("MQTT", "Kết nối MQTT thành công với ID: " + CLIENT_ID + ", Broker: " + BROKER_URL);
            runOnUiThread(() -> {
                statusTextView.setText("Kết nối MQTT thành công\nVui lòng quét mã QR");
                Toast.makeText(MainActivity.this, "Kết nối MQTT thành công", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định";
            Log.e("MQTT", "Lỗi khi kết nối MQTT: " + errorMsg, e);
            runOnUiThread(() -> {
                statusTextView.setText("Lỗi kết nối MQTT: " + errorMsg);
                Toast.makeText(MainActivity.this, "Lỗi kết nối MQTT: " + errorMsg, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void publishToMqtt(String cccdNumber, LocalDateTime accessTime, String result) {
        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("cccdId", cccdNumber != null ? cccdNumber : "unknown");
            messageJson.put("accessTime", formatDateTime(accessTime));
            messageJson.put("status", result.equalsIgnoreCase("allowed") ? 1 : 0);

            String message = messageJson.toString();
            Log.d("MQTT", "Chuẩn bị gửi message JSON: " + message);

            // Gọi hàm publish của MqttHandler
            mqttHandler.publish(TOPIC, message);
            Log.d("MQTT", "Đã gửi message: " + message);
            runOnUiThread(() -> {
                statusTextView.setText("Gửi MQTT thành công");
                Toast.makeText(MainActivity.this, "Gửi MQTT thành công", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định";
            Log.e("MQTT", "Lỗi gửi message: " + errorMsg, e);
            runOnUiThread(() -> {
                statusTextView.setText("Lỗi gửi MQTT: " + errorMsg);
                Toast.makeText(MainActivity.this, "Lỗi gửi MQTT: " + errorMsg, Toast.LENGTH_LONG).show();
            });
            connectToMqttBroker();
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return dateTime.format(formatter);
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

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
        if (isQrScanned || isProcessingApi) {
            Log.d("verifyCccd", "QR đã được xác minh hoặc đang xử lý API, bỏ qua");
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
                            StringBuilder registrationInfo = new StringBuilder();
                            registrationInfo.append("Xác minh thành công\n");
                            registrationInfo.append("MRZ: ").append(qrRegistration.getMrz() != null ? qrRegistration.getMrz() : "Không xác định").append("\n");
                            registrationInfo.append("Mục đích: ").append(qrRegistration.getPurpose() != null ? qrRegistration.getPurpose() : "Không xác định").append("\n");
                            registrationInfo.append("Thời gian bắt đầu: ").append(qrRegistration.getStartTime() != null ? qrRegistration.getStartTime() : "N/A").append("\n");
                            registrationInfo.append("Thời gian kết thúc: ").append(qrRegistration.getEndTime() != null ? qrRegistration.getEndTime() : "N/A").append("\n");
                            registrationInfo.append("ID: ").append(qrRegistration.getId());

                            String toastMessage = registrationInfo.toString();
                            Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_LONG).show();

                            qrMrz = qrRegistration.getMrz();
                            qrRegistration.setCccdNumber(cccdNumber);
                            isQrScanned = true;
                            runOnUiThread(() -> {
                                String currentText = statusTextView.getText().toString();
                                if (!currentText.contains("Vui lòng áp thẻ CCCD")) {
                                    statusTextView.setText(currentText + "\n Vui lòng áp thẻ CCCD để đọc NFC");
                                    Toast.makeText(MainActivity.this, "Áp thẻ CCCD để đọc NFC", Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            final String errorMessage = "Bạn chưa đăng ký\nServer trả về: " +
                                    (regResponse.getErrDesc() != null ? regResponse.getErrDesc() : "Không có dữ liệu");
                            runOnUiThread(() -> {
                                statusTextView.setText(errorMessage);
                                Toast.makeText(MainActivity.this, "Người dùng chưa đăng ký", Toast.LENGTH_LONG).show();
                            });
                            publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                            resetState();
                        }
                    } else {
                        StringBuilder errorBuilder = new StringBuilder("Lỗi server: Bạn chưa đăng ký");
                        try {
                            if (response.errorBody() != null) {
                                errorBuilder.append("\nServer trả về: ").append(response.errorBody().string());
                            }
                        } catch (IOException e) {
                            errorBuilder.append("\n(Lỗi khi đọc body lỗi: ").append(e.getMessage()).append(")");
                        }
                        final String errorMessage = errorBuilder.toString();
                        int statusCode = response.code();
                        Log.e("verifyCccd", "Lỗi server: Mã lỗi HTTP " + statusCode);
                        Log.e("verifyCccd", errorMessage);
                        runOnUiThread(() -> {
                            statusTextView.setText(errorMessage);
                            Toast.makeText(MainActivity.this, "Người dùng chưa đăng ký (" + statusCode + ")", Toast.LENGTH_LONG).show();
                        });
                        publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                        resetState();
                    }
                } finally {
                    isProcessingApi = false;
                }
            }

            @Override
            public void onFailure(Call<RegistrationResponse> call, Throwable t) {
                try {
                    final String errorMessage = "Lỗi kết nối server: " + t.getMessage();
                    Log.e("verifyCccd", "Lỗi kết nối: " + t.getMessage(), t);
                    runOnUiThread(() -> {
                        statusTextView.setText(errorMessage);
                        Toast.makeText(MainActivity.this, "Lỗi kết nối server, thử lại sau", Toast.LENGTH_LONG).show();
                    });
                    publishToMqtt(cccdNumber, LocalDateTime.now(), "denied");
                    resetState();
                } finally {
                    isProcessingApi = false;
                }
            }
        });
    }

    private boolean isValidCccdId(String cccdId) {
        return cccdId != null && cccdId.matches("^[0-9]{12}$");
    }

    private void readNfcWithMrz(Tag tag, String mrz, Registration reg, String qrCccdNumber) {
        ExecutorService nfcExecutor = Executors.newSingleThreadExecutor();
        nfcExecutor.execute(() -> {
            PassportService passportService = null;
            IsoDep isoDep = null;
            try {
                Log.d("NFC", "Bắt đầu đọc NFC cho CCCD: " + qrCccdNumber + ", MRZ: " + mrz);
                isoDep = IsoDep.get(tag);
                if (isoDep == null) {
                    throw new IOException("Thẻ không hỗ trợ IsoDep");
                }
                Log.d("NFC", "Kết nối IsoDep...");
                isoDep.connect();
                isoDep.setTimeout(10000);

                Log.d("NFC", "Khởi tạo CardService và PassportService...");
                CardService cardService = CardService.getInstance(isoDep);
                passportService = new PassportService(
                        cardService,
                        PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH,
                        PassportService.DEFAULT_MAX_BLOCKSIZE,
                        false,
                        false
                );
                passportService.open();

                Log.d("NFC", "Kiểm tra MRZ...");
                if (mrz == null || mrz.length() != 90) {
                    throw new IllegalArgumentException("MRZ không hợp lệ, độ dài: " + (mrz != null ? mrz.length() : "null"));
                }

                String documentNumberRaw = mrz.substring(18, 29);
                String documentNumber = documentNumberRaw.replace("<", "");
                String dob = mrz.substring(30, 36);
                String expiry = mrz.substring(38, 44);

                Log.d("NFC", "Tạo BACKey: docNumber=" + documentNumber + ", dob=" + dob + ", expiry=" + expiry);

                BACKey bacKey = new BACKey(documentNumber, dob, expiry);
                Log.d("NFC", "BAC key: " + bacKey);

                boolean paceSucceeded = false;
                try {
                    Log.d("NFC", "Thử PACE...");
                    CardAccessFile cardAccessFile = new CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS));
                    Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();
                    for (SecurityInfo securityInfo : securityInfos) {
                        if (securityInfo instanceof PACEInfo) {
                            Log.d("NFC", "Thẻ hỗ trợ PACE, thực hiện PACE...");
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
                    Log.w("NFC", "PACE thất bại: " + e.getMessage(), e);
                }

                Log.d("NFC", "Chọn applet, PACE thành công: " + paceSucceeded);
                passportService.sendSelectApplet(paceSucceeded);

                if (!paceSucceeded) {
                    Log.d("NFC", "Thử BAC...");
                    try {
                        passportService.getInputStream(PassportService.EF_COM).read();
                    } catch (Exception e) {
                        Log.d("NFC", "EF_COM không đọc được, chọn lại applet và thử BAC...");
                        passportService.sendSelectApplet(false);
                        passportService.doBAC(bacKey);
                    }
                }

                Log.d("NFC", "Đọc DG1...");
                InputStream inputStream = passportService.getInputStream(PassportService.EF_DG1);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                byte[] data = baos.toByteArray();

                Log.d("NFC", "Tạo DG1File...");
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DG1File dg1File = new DG1File(bais);
                bais.close();

                String cccdNumber = dg1File.getMRZInfo().getDocumentNumber();
                String test = dg1File.getMRZInfo().getDocumentCode();
                Log.d("NFC", "CCCD số từ thẻ: " + cccdNumber);
                Log.d("NFC", "test: " + test);
                LocalDateTime now = LocalDateTime.now();

                Log.d("NFC", "Kiểm tra điều kiện...");
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                LocalDateTime startTime = LocalDateTime.parse(reg.getStartTime(), formatter);
                LocalDateTime endTime = LocalDateTime.parse(reg.getEndTime(), formatter);

                if (cccdNumber != null && qrCccdNumber != null && reg.getCccdNumber() != null) {
                    String qrCccdNumberLast9 = qrCccdNumber.length() >= 9 ? qrCccdNumber.substring(qrCccdNumber.length() - 9) : qrCccdNumber;
                    String regCccdNumberLast9 = reg.getCccdNumber().length() >= 9 ? reg.getCccdNumber().substring(reg.getCccdNumber().length() - 9) : reg.getCccdNumber();

                    Log.d("NFC", "So sánh CCCD - Từ thẻ: " + cccdNumber + ", Từ QR (9 số cuối): " + qrCccdNumberLast9 + ", Từ đăng ký (9 số cuối): " + regCccdNumberLast9);

                    if (cccdNumber.equals(qrCccdNumberLast9) &&
                            cccdNumber.equals(regCccdNumberLast9) &&
                            reg.getStatus() == 1 &&
                            now.isAfter(startTime) && now.isBefore(endTime)) {
                        Log.d("NFC", "Xác minh NFC thành công: " + reg.getPurpose());
                        runOnUiThread(() -> {
                            statusTextView.setText("Xác minh hợp lệ: " + reg.getPurpose());
                            Toast.makeText(MainActivity.this, "Cửa mở thành công", Toast.LENGTH_LONG).show();
                        });
                        publishToMqtt(qrCccdNumber, now, "allowed");
                        openDoor();
                    } else {
                        String reason = "Thông tin không khớp";
                        if (reg.getStatus() != 1 && reg.getStatus() != null) {
                            reason = "Chưa được duyệt";
                        } else if (!now.isAfter(startTime) || !now.isBefore(endTime)) {
                            reason = "Ngoài thời gian cho phép";
                        } else if (!cccdNumber.equals(qrCccdNumberLast9)) {
                            reason = "Số CCCD từ thẻ không khớp với QR";
                        } else if (!cccdNumber.equals(regCccdNumberLast9)) {
                            reason = "Số CCCD từ thẻ không khớp với đăng ký";
                        }

                        final String finalReason = reason;
                        Log.e("NFC", "Thời gian bắt đầu: " + startTime.toString());
                        Log.e("NFC", "Xác minh NFC thất bại: " + reason);
                        runOnUiThread(() -> {
                            statusTextView.setText(finalReason);
                            Toast.makeText(MainActivity.this, finalReason, Toast.LENGTH_LONG).show();
                        });
                        publishToMqtt(qrCccdNumber, now, "denied");
                    }
                } else {
                    String reason = "Dữ liệu CCCD không hợp lệ";
                    Log.e("NFC", "Xác minh NFC thất bại: " + reason);
                    runOnUiThread(() -> {
                        statusTextView.setText(reason);
                        Toast.makeText(MainActivity.this, reason, Toast.LENGTH_LONG).show();
                    });
                    publishToMqtt(qrCccdNumber != null ? qrCccdNumber : "unknown", now, "denied");
                }
            } catch (IllegalArgumentException e) {
                Log.e("NFC", "Lỗi MRZ không hợp lệ: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    statusTextView.setText("MRZ không hợp lệ: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "MRZ không hợp lệ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                Log.e("NFC", "Lỗi IO khi đọc thẻ: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    statusTextView.setText("Lỗi IO: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Lỗi IO khi đọc thẻ NFC: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e("NFC", "Lỗi không xác định khi đọc thẻ: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    statusTextView.setText("Lỗi đọc thẻ: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Lỗi đọc thẻ NFC: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                Log.d("NFC", "Đóng PassportService và IsoDep...");
                try {
                    if (passportService != null) {
                        passportService.close();
                    }
                    if (isoDep != null && isoDep.isConnected()) {
                        isoDep.close();
                    }
                } catch (Exception e) {
                    Log.e("NFC", "Lỗi khi đóng kết nối: " + e.getMessage(), e);
                }
                Log.d("NFC", "Hoàn tất xử lý NFC, reset trạng thái");
                resetState();
                nfcExecutor.shutdown();
            }
        });
    }

    private String formatDate(String date) {
        if (date == null || date.length() != 6) {
            return date;
        }
        return date.substring(4, 6) + date.substring(2, 4) + date.substring(0, 2);
    }

    private void handleNfcIntent(Intent intent) {
        Log.d("NFC_DEBUG", "Đang xử lý nfc intent: " + intent.getAction());
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Log.d("NFC_DEBUG", "NFC tag: " + (nfcTag != null ? nfcTag.toString() : "null"));
            if (nfcTag != null) {
                Log.d("NFC_DEBUG", "Tag technologies: " + Arrays.toString(nfcTag.getTechList()));
                if (isQrScanned && qrMrz != null && qrRegistration != null) {
                    Log.d("NFC_DEBUG", "Điều kiện NFC hợp lệ, bắt đầu đọc thẻ");
                    runOnUiThread(() -> {
                        statusTextView.setText("Đang đọc thẻ NFC...");
                        Toast.makeText(this, "Đang đọc thẻ NFC...", Toast.LENGTH_SHORT).show();
                    });
                    readNfcWithMrz(nfcTag, qrMrz, qrRegistration, qrCccdNumber);
                } else {
                    Log.e("NFC_DEBUG", "Điều kiện NFC không hợp lệ: isQrScanned=" + isQrScanned +
                            ", qrMrz=" + qrMrz + ", qrRegistration=" + qrRegistration);
                    runOnUiThread(() -> {
                        statusTextView.setText("Vui lòng quét mã QR trước");
                        Toast.makeText(this, "Quét mã QR trước khi áp thẻ NFC", Toast.LENGTH_LONG).show();
                    });
                }
            } else {
                Log.e("NFC_DEBUG", "NFC tag is null");
                runOnUiThread(() -> {
                    statusTextView.setText("Không nhận diện được thẻ NFC");
                    Toast.makeText(this, "Không nhận diện được thẻ NFC", Toast.LENGTH_LONG).show();
                });
            }
        } else {
            Log.e("NFC_DEBUG", "Invalid NFC action: " + action);
            runOnUiThread(() -> {
                statusTextView.setText("Intent NFC không hợp lệ: " + action);
                Toast.makeText(this, "Intent NFC không hợp lệ: " + action, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void resetState() {
        qrCccdNumber = null;
        qrMrz = null;
        qrRegistration = null;
        isQrScanned = false;
        isProcessingApi = false;
        nfcTag = null;
        runOnUiThread(() -> statusTextView.setText("Vui lòng quét mã QR"));
    }

    private void openDoor() {
        runOnUiThread(() -> Toast.makeText(this, "Cửa mở", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("NFC_DEBUG", "onNewIntent được gọi với action: " + intent.getAction() +
                ", isQrScanned: " + isQrScanned + ", qrMrz: " + qrMrz + ", qrRegistration: " + qrRegistration);
        setIntent(intent);
        if (isQrScanned) {
            Log.d("NFC_DEBUG", "isQrScanned=true, xử lý NFC intent");
            handleNfcIntent(intent);
        } else {
            Log.d("NFC_DEBUG", "isQrScanned=false, yêu cầu quét QR trước");
            Toast.makeText(this, "Vui lòng quét mã QR trước khi áp thẻ", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("NFC_DEBUG", "onResume called");
        if (nfcAdapter != null) {
            if (nfcAdapter.isEnabled()) {
                Log.d("NFC_DEBUG", "NFC enabled, setting up foreground dispatch");
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                IntentFilter[] intentFilters = new IntentFilter[] {
                        new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                        new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                        new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
                };
                String[][] techList = new String[][] { new String[] { "android.nfc.tech.IsoDep" } };
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techList);
            } else {
                Log.e("NFC_DEBUG", "NFC is disabled on device");
                Toast.makeText(this, "Vui lòng bật NFC trong cài đặt", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e("NFC_DEBUG", "NFC adapter is null");
            Toast.makeText(this, "Thiết bị không hỗ trợ NFC", Toast.LENGTH_LONG).show();
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusTextView.setText("Cần quyền camera để quét QR");
        }
    }

    @Override
    protected void onDestroy() {
        // Ngắt kết nối MQTT khi activity bị hủy
        super.onDestroy();
        if (mqttHandler != null) {
            mqttHandler.disconnect();
        }
        cameraExecutor.shutdown();
    }
}