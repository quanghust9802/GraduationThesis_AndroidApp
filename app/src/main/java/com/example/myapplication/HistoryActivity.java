package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
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

public class HistoryActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://192.168.1.139:7244/";
    private RecyclerView recyclerView;
    private HistoryAdapter historyAdapter;
    private List<AccessLog> accessLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

//        // Thiết lập Toolbar
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setTitle("Lịch sử ra vào");
//            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        }

        // Thiết lập RecyclerView
        recyclerView = findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        accessLogs = new ArrayList<>();
        historyAdapter = new HistoryAdapter(accessLogs);
        recyclerView.setAdapter(historyAdapter);

        fetchAccessHistory();
    }

    private void fetchAccessHistory() {
        OkHttpClient client = getUnsafeOkHttpClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<AccessLogResponse> call = apiService.getAccessHistory();

        call.enqueue(new Callback<AccessLogResponse>() {
            @Override
            public void onResponse(Call<AccessLogResponse> call, Response<AccessLogResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AccessLogResponse logResponse = response.body();
                    if (logResponse.getErrCode() == 200 && logResponse.getData() != null) {
                        accessLogs.clear();
                        accessLogs.addAll(logResponse.getData());
                        historyAdapter.notifyDataSetChanged();
                    } else {
                        String errorMsg = "Không thể tải lịch sử: " +
                                (logResponse.getErrDesc() != null ? logResponse.getErrDesc() : "Không có dữ liệu");
                        Toast.makeText(HistoryActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        Log.e("HistoryActivity", errorMsg);
                    }
                } else {
                    String errorMsg = "Không thể tải lịch sử: ";
                    if (response.errorBody() != null) {
                        try {
                            errorMsg += response.errorBody().string();
                        } catch (Exception e) {
                            errorMsg += response.message();
                        }
                    } else {
                        errorMsg += response.message();
                    }
                    Toast.makeText(HistoryActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    Log.e("HistoryActivity", errorMsg);
                }
            }

            @Override
            public void onFailure(Call<AccessLogResponse> call, Throwable t) {
                Toast.makeText(HistoryActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("HistoryActivity", "Lỗi: " + t.getMessage());
            }
        });
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}