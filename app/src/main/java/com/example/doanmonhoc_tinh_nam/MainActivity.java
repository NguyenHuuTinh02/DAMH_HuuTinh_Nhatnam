package com.example.doanmonhoc_tinh_nam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;

    private TextView tvResult, tvTime, tvCountdown;
    private EditText valueedt;
    private SpeechRecognizer speechRecognizer;
    private FirebaseDatabase database;
    private DatabaseReference ref;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private int selectedTimeInSeconds = 0; // Thời gian đã chọn

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kiểm tra và yêu cầu quyền microphone
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
            }
        }

        // Khởi tạo UI
        Button btnStart = findViewById(R.id.btnStart);
        Button pushbtn = findViewById(R.id.Pushbutton);
        //Button getbtn = findViewById(R.id.Getbutton);
        Button btnTurnOn = findViewById(R.id.btnTurnOn);
        Button btnTurnOff = findViewById(R.id.btnTurnOff);
        Button btnSetTime = findViewById(R.id.btnSetTime);

        tvResult = findViewById(R.id.tvResult);
        valueedt = findViewById(R.id.LEDstatuseditText);
        tvTime = findViewById(R.id.tvTime);
        tvCountdown = findViewById(R.id.tvCountdown);

        // Trỏ tới Firebase
        database = FirebaseDatabase.getInstance();
        ref = database.getReference("ESP8266 NodeMCU Board");

        // Lắng nghe dữ liệu từ Firebase ngay khi ứng dụng khởi động
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Lấy trạng thái LED từ Firebase
                    String ledStatus = snapshot.child("Outputs/Digital/LED").getValue(String.class);
                    if (ledStatus != null) {
                        // Hiển thị trạng thái LED trong tvResult
                        tvResult.setText("Trạng thái LED: " + ("1".equals(ledStatus) ? "Bật" : "Tắt"));
                    } else {
                        tvResult.setText("Trạng thái LED không xác định");
                    }

                    // Lấy thời gian còn lại từ Firebase
                    Long timeoutMillis = snapshot.child("Settings/Timeout").getValue(Long.class);
                    if (timeoutMillis != null && timeoutMillis > 0) {
                        int totalSeconds = (int) (timeoutMillis / 1000);
                        int hours = totalSeconds / 3600;
                        int minutes = (totalSeconds % 3600) / 60;
                        int seconds = totalSeconds % 60;

                        String formattedTime = String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds);
                        tvCountdown.setText(formattedTime);
                    } else {
                        tvCountdown.setText("Thời gian còn lại: 00:00:00");
                    }
                } else {
                    tvResult.setText("Không có dữ liệu LED");
                    tvCountdown.setText("Thời gian còn lại: 00:00:00");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Lỗi Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


        // Khởi tạo SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Toast.makeText(MainActivity.this, "Đang sẵn sàng nhận diện giọng nói", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBeginningOfSpeech() {
                Toast.makeText(MainActivity.this, "Đang nhận diện giọng nói...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                Toast.makeText(MainActivity.this, "Lỗi nhận diện giọng nói: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) {
                    String spokenText = data.get(0).toLowerCase();
                    tvResult.setText(spokenText);

                    String[] turnOnKeywords = {"bật", "on", "mở", "bật đèn", "hãy bật", "hãy bật đèn"};
                    String[] turnOffKeywords = {"tắt", "off", "đóng", "tắt đèn", "hãy tắt", "hãy tắt đèn"};

                    if (containsAny(spokenText, turnOnKeywords)) {
                        selectedTimeInSeconds = 60;
                        ref.child("Outputs/Digital/LED").setValue("1");
                        ref.child("Settings/Timeout").setValue(selectedTimeInSeconds * 1000);
                        Toast.makeText(MainActivity.this, "LED đã bật với thời gian mặc định 1 phút", Toast.LENGTH_SHORT).show();
                        startCountdown(selectedTimeInSeconds);
                    } else if (containsAny(spokenText, turnOffKeywords)) {
                        ref.child("Outputs/Digital/LED").setValue("0");
                        selectedTimeInSeconds = 0;
                        ref.child("Settings/Timeout").setValue(0);
                        stopCountdown();
                        Toast.makeText(MainActivity.this, "LED đã tắt, thời gian Firebase đặt lại 0", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Không nhận diện được lệnh", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    tvResult.setText("Không nhận diện được giọng nói");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        btnStart.setOnClickListener(v -> startSpeechRecognition());

        pushbtn.setOnClickListener(v -> {
            String ledstatus = valueedt.getText().toString().trim().toLowerCase();

            // Các từ khóa bật và tắt
            String[] turnOnKeywords = {"1", "bật", "on", "mở", "bật đèn", "hãy bật", "hãy bật đèn"};
            String[] turnOffKeywords = {"0", "tắt", "off", "đóng", "tắt đèn", "hãy tắt", "hãy tắt đèn"};

            // Kiểm tra trạng thái nhập
            if (containsAny(ledstatus, turnOnKeywords)) {
                // Nếu bật đèn
                ref.child("Outputs/Digital/LED").setValue("1");

                // Nếu thời gian chưa được cài đặt, mặc định là 1 phút
                if (selectedTimeInSeconds == 0) {
                    selectedTimeInSeconds = 60; // 1 phút
                    ref.child("Settings/Timeout").setValue(selectedTimeInSeconds * 1000);
                }

                // Bắt đầu đếm ngược
                startCountdown(selectedTimeInSeconds);
                Toast.makeText(MainActivity.this, "Đã lưu trạng thái: Bật đèn. Thời gian: " + selectedTimeInSeconds / 60 + " phút", Toast.LENGTH_SHORT).show();

            } else if (containsAny(ledstatus, turnOffKeywords)) {
                // Nếu tắt đèn
                ref.child("Outputs/Digital/LED").setValue("0");
                selectedTimeInSeconds = 0; // Đặt thời gian về 0
                ref.child("Settings/Timeout").setValue(0);

                // Dừng đếm ngược
                stopCountdown();
                tvCountdown.setText("Thời gian còn lại: 00:00:00");
                Toast.makeText(MainActivity.this, "Đã lưu trạng thái: Tắt đèn. Thời gian: 0 phút", Toast.LENGTH_SHORT).show();

            } else {
                // Nếu nhập sai trạng thái
                Toast.makeText(MainActivity.this, "Lỗi: Chỉ nhập '1', '0' hoặc các từ khóa hợp lệ (bật/tắt)", Toast.LENGTH_SHORT).show();
                valueedt.setText(""); // Xóa giá trị nhập sai
            }
        });


        btnTurnOn.setOnClickListener(v -> {
            stopCountdown();
            ref.child("Outputs/Digital/LED").setValue("1").addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (selectedTimeInSeconds > 0) {
                        ref.child("Settings/Timeout").setValue(selectedTimeInSeconds * 1000).addOnCompleteListener(timeoutTask -> {
                            if (timeoutTask.isSuccessful()) {
                                startCountdown(selectedTimeInSeconds);
                            } else {
                                Toast.makeText(MainActivity.this, "Lỗi khi cập nhật thời gian lên Firebase", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "Thời gian chưa được cài đặt", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Lỗi khi bật đèn trên Firebase", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnTurnOff.setOnClickListener(v -> {
            ref.child("Outputs/Digital/LED").setValue("0");
            ref.child("Settings/Timeout").setValue(0);
            stopCountdown();
            tvCountdown.setText("Thời gian còn lại: 00:00:00");
            Toast.makeText(MainActivity.this, "LED đã tắt và thời gian đặt lại 0", Toast.LENGTH_SHORT).show();
        });

        btnSetTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedTimeInSeconds = hourOfDay * 3600 + minute * 60;
                String timeDisplay = String.format("Thời gian sáng: %d giờ %d phút", hourOfDay, minute);
                tvTime.setText(timeDisplay);
                ref.child("Settings/Timeout").setValue(selectedTimeInSeconds * 1000);
            }, 0, 0, true);

            timePickerDialog.show();
        });
    }

    private void startCountdown(int startTimeInSeconds) {
        if (isTimerRunning) {
            stopCountdown();
        }

        isTimerRunning = true;
        countDownTimer = new CountDownTimer(startTimeInSeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int totalSeconds = (int) (millisUntilFinished / 1000);
                int hours = totalSeconds / 3600;
                int minutes = (totalSeconds % 3600) / 60;
                int seconds = totalSeconds % 60;

                tvCountdown.setText(String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds));
                ref.child("Settings/Timeout").setValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                ref.child("Outputs/Digital/LED").setValue("0");
                ref.child("Settings/Timeout").setValue(0);
                tvCountdown.setText("Thời gian còn lại: 00:00:00");
                isTimerRunning = false;
            }
        }.start();
    }

    private void stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            isTimerRunning = false;
        }
    }

    private void startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition không khả dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("vi", "VN"));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói gì đó...");
        speechRecognizer.startListening(intent);
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

