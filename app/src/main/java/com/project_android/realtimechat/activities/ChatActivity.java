package com.project_android.realtimechat.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.project_android.realtimechat.adapters.ChatAdapter;
import com.project_android.realtimechat.ai.GeminiAIService;
import com.project_android.realtimechat.databinding.ActivityChatBinding;
import com.project_android.realtimechat.models.ChatMessage;
import com.project_android.realtimechat.models.User;
import com.project_android.realtimechat.utilities.Constants;
import com.project_android.realtimechat.utilities.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private GeminiAIService geminiAIService;

    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadReceiverDetails();
        init();
        setListeners();
        listenMessages();
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        database = FirebaseFirestore.getInstance();
        geminiAIService = new GeminiAIService();

        chatMessages = new ArrayList<>();

        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );

        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);

        if (receiverUser == null) {
            finish();
            return;
        }

        binding.textName.setText(receiverUser.name);

        if (isChatWithAI()) {
            binding.textAvailability.setText("AI Assistant");
            binding.textAvailability.setVisibility(View.VISIBLE);
        }
    }

    private boolean isChatWithAI() {
        return receiverUser != null && (
                receiverUser.isAI
                        || "AI Assistant".equalsIgnoreCase(receiverUser.name)
                        || "ai@chatbot.local".equalsIgnoreCase(receiverUser.email)
                        || "AI_ASSISTANT".equals(receiverUser.id)
        );
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageText = binding.inputMessage.getText().toString().trim();

        if (messageText.isEmpty()) {
            return;
        }

        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, messageText);
        message.put(Constants.KEY_TIMESTAMP, new Date());

        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);

        if (conversionId != null) {
            updateConversion(messageText);
        } else {
            HashMap<String, Object> conversion = new HashMap<>();

            conversion.put(
                    Constants.KEY_SENDER_ID,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );

            conversion.put(
                    Constants.KEY_SENDER_NAME,
                    preferenceManager.getString(Constants.KEY_NAME)
            );

            conversion.put(
                    Constants.KEY_SENDER_IMAGE,
                    preferenceManager.getString(Constants.KEY_IMAGE)
            );

            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, messageText);
            conversion.put(Constants.KEY_TIMESTAMP, new Date());

            addConversion(conversion);
        }

        binding.inputMessage.setText(null);

        if (isChatWithAI()) {
            askAIAndSaveReply(messageText);
        }
    }

    private void askAIAndSaveReply(String userMessage) {
        geminiAIService.askAI(userMessage, new GeminiAIService.AIResponseCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> saveAIReply(response));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (error != null && error.contains("429")) {
                        saveAIReply("AI đang bị giới hạn lượt dùng, bạn thử lại sau khoảng 30 giây nhé.");
                    } else if (error != null && error.contains("503")) {
                        saveAIReply("AI đang quá tải, bạn thử lại sau vài giây nhé.");
                    } else {
                        saveAIReply("Xin lỗi, hiện tại AI chưa trả lời được. Bạn thử lại sau nhé.");
                    }
                });
            }
        });
    }

    private void saveAIReply(String aiMessage) {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, receiverUser.id);
        message.put(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_MESSAGE, aiMessage);
        message.put(Constants.KEY_TIMESTAMP, new Date());

        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);

        if (conversionId != null) {
            updateConversion(aiMessage);
        }
    }

    private void listenAvailabilityOfReceiver() {
        if (isChatWithAI()) {
            binding.textAvailability.setText("AI Assistant");
            binding.textAvailability.setVisibility(View.VISIBLE);
            return;
        }

        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .addSnapshotListener(ChatActivity.this, (value, error) -> {

                    if (error != null) {
                        return;
                    }

                    if (value != null) {

                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {

                            int availability = Objects.requireNonNull(
                                    value.getLong(Constants.KEY_AVAILABILITY)
                            ).intValue();

                            isReceiverAvailable = availability == 1;
                        }

                        receiverUser.token =
                                value.getString(Constants.KEY_FCM_TOKEN);
                    }

                    if (isReceiverAvailable) {
                        binding.textAvailability.setText("Online");
                        binding.textAvailability.setVisibility(View.VISIBLE);
                    } else {
                        binding.textAvailability.setVisibility(View.GONE);
                    }
                });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(
                        Constants.KEY_SENDER_ID,
                        preferenceManager.getString(Constants.KEY_USER_ID)
                )
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(
                        Constants.KEY_RECEIVER_ID,
                        preferenceManager.getString(Constants.KEY_USER_ID)
                )
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }

        if (value != null) {
            int count = chatMessages.size();

            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {

                    ChatMessage chatMessage = new ChatMessage();

                    chatMessage.senderId = documentChange.getDocument()
                            .getString(Constants.KEY_SENDER_ID);

                    chatMessage.receiverId = documentChange.getDocument()
                            .getString(Constants.KEY_RECEIVER_ID);

                    chatMessage.message = documentChange.getDocument()
                            .getString(Constants.KEY_MESSAGE);

                    chatMessage.dateTime = getReadableDateTime(
                            documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP)
                    );

                    chatMessage.dateObject = documentChange.getDocument()
                            .getDate(Constants.KEY_TIMESTAMP);

                    chatMessages.add(chatMessage);
                }
            }

            Collections.sort(chatMessages, (obj1, obj2) ->
                    obj1.dateObject.compareTo(obj2.dateObject));

            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(
                        count,
                        chatMessages.size() - count
                );

                binding.chatRecyclerView.smoothScrollToPosition(
                        chatMessages.size() - 1
                );
            }

            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }

        binding.progressBar.setVisibility(View.GONE);

        if (conversionId == null) {
            checkForConversion();
        }
    };

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage == null || encodedImage.trim().isEmpty()) {
            return null;
        }

        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat(
                "MMMM dd, yyyy - hh:mm a",
                Locale.getDefault()
        ).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference ->
                        conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = database
                .collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(conversionId);

        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion() {
        if (!chatMessages.isEmpty()) {

            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );

            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful()
                && task.getResult() != null
                && task.getResult().getDocuments().size() > 0) {

            DocumentSnapshot documentSnapshot = task.getResult()
                    .getDocuments()
                    .get(0);

            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}