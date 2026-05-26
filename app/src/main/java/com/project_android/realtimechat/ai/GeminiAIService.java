package com.project_android.realtimechat.ai;

import androidx.annotation.NonNull;

import com.project_android.realtimechat.BuildConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public class GeminiAIService {

    public interface AIResponseCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    private final GeminiApi geminiApi;

    public GeminiAIService() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        geminiApi = retrofit.create(GeminiApi.class);
    }

    public void askAI(String userMessage, AIResponseCallback callback) {
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            callback.onError("Chưa cấu hình GEMINI_API_KEY trong local.properties");
            return;
        }

        String prompt =
                "Bạn là AI Assistant trong ứng dụng chat realtime. " +
                        "Hãy trả lời bằng tiếng Việt, ngắn gọn, thân thiện, dễ hiểu. " +
                        "Luôn trả lời người dùng, không được im lặng. " +
                        "Nếu người dùng hỏi thông tin thời gian thực như thời tiết, giá cả, tin tức, lịch hôm nay, " +
                        "hãy nói rằng bạn không thể xem dữ liệu realtime trực tiếp và gợi ý người dùng kiểm tra Google hoặc ứng dụng phù hợp. " +
                        "Nếu người dùng chào hỏi ngắn như hello, hi, yo thì hãy chào lại tự nhiên.\n\n" +
                        "Người dùng hỏi: " + userMessage;

        GeminiRequest request = new GeminiRequest(prompt);

        geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeminiResponse> call,
                                           @NonNull Response<GeminiResponse> response) {
                        if (!response.isSuccessful()) {
                            String errorBody = "";

                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (IOException e) {
                                errorBody = e.getMessage();
                            }

                            callback.onError("Lỗi API: " + response.code() + "\n" + errorBody);
                            return;
                        }

                        GeminiResponse body = response.body();

                        if (body == null
                                || body.candidates == null
                                || body.candidates.isEmpty()
                                || body.candidates.get(0).content == null
                                || body.candidates.get(0).content.parts == null
                                || body.candidates.get(0).content.parts.isEmpty()
                                || body.candidates.get(0).content.parts.get(0).text == null) {
                            callback.onError("AI không trả về nội dung");
                            return;
                        }

                        callback.onSuccess(body.candidates.get(0).content.parts.get(0).text.trim());
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                        callback.onError("Không gọi được Gemini API: " + t.getMessage());
                    }
                });
    }

    private interface GeminiApi {
        @POST("v1beta/models/gemini-2.5-flash-lite:generateContent")
        Call<GeminiResponse> generateContent(
                @Query("key") String apiKey,
                @Body GeminiRequest request
        );
    }

    private static class GeminiRequest {
        List<Content> contents;

        GeminiRequest(String text) {
            contents = new ArrayList<>();

            Content content = new Content();
            content.parts = new ArrayList<>();

            Part part = new Part();
            part.text = text;

            content.parts.add(part);
            contents.add(content);
        }
    }

    private static class Content {
        List<Part> parts;
    }

    private static class Part {
        String text;
    }

    private static class GeminiResponse {
        List<Candidate> candidates;
    }

    private static class Candidate {
        Content content;
    }
}