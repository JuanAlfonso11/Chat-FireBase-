package com.example.fbct2.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.fbct2.adapters.ChatAdapter;
import com.example.fbct2.databinding.ActivityChatBinding;
import com.example.fbct2.models.ChatMessage;
import com.example.fbct2.models.User;
import com.example.fbct2.network.ApiClient;
import com.example.fbct2.network.ApiService;
import com.example.fbct2.utilities.Constants;
import com.example.fbct2.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {
    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenerMessages();
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, getBitmapFormEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID));
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
        checkForConversion(); // Verificar si existe una conversación ya creada
    }

    private void sendMessage() {
        String messageText = binding.inputMessage.getText().toString().trim();

        if (messageText.isEmpty()) {
            showToast("No se puede enviar un mensaje vacío");
            return;
        }

        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, messageText);
        message.put(Constants.KEY_TIMESTAMP, new Date());

        // Enviar mensaje a la base de datos
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message)
                .addOnSuccessListener(documentReference -> {
                    if (conversionId != null) {
                        // Si ya existe una conversación, la actualizamos
                        updateConversion(messageText);
                    } else {
                        // Verifica si ya existe una conversación antes de crear una nueva
                        checkForExistingConversationAndCreate(messageText);
                    }
                    binding.inputMessage.setText(null);
                });

        if (!isReceiverAvailable) {
            sendNotification(messageText);
        }
    }


    private void checkForConversionAndCreate(String messageText) {
        checkForConversationRemotely(preferenceManager.getString(Constants.KEY_USER_ID), receiverUser.id);
        checkForConversationRemotely(receiverUser.id, preferenceManager.getString(Constants.KEY_USER_ID));

        if (conversionId == null) {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, messageText);
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        if (conversionId != null) {
            DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
            documentReference.update(Constants.KEY_LAST_MESSAGE, message, Constants.KEY_TIMESTAMP, new Date());
        }
    }


    private void listenerMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
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
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
    };

    private Bitmap getBitmapFormEncodedString(String encodedImage) {
        if (encodedImage != null && !encodedImage.isEmpty()) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null; // Manejar casos donde no haya imagen
        }
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        if (receiverUser != null) {
            Log.d("ChatActivity", "Receiver details loaded: " + receiverUser.name);
            binding.textName.setText(receiverUser.name);
            Bitmap bitmap = getBitmapFormEncodedString(receiverUser.image);
            if (bitmap != null) {
                binding.imageProfile.setImageBitmap(bitmap);
            } else {
                binding.imageProfile.setImageResource(android.R.color.transparent);
            }
        } else {
            Log.e("ChatActivity", "Receiver is null");
            showToast("No se pudo cargar la información del receptor.");
        }
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void checkForConversion() {
        if(chatMessages.size() != 0) {
            checkForConversationRemotely(preferenceManager.getString(Constants.KEY_USER_ID), receiverUser.id);
            checkForConversationRemotely(receiverUser.id, preferenceManager.getString(Constants.KEY_USER_ID));
        }
    }
    private void checkForExistingConversationAndCreate(String messageText) {
        // Verificar si ya existe una conversación en ambas direcciones (A->B y B->A)
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().size() > 0) {
                        conversionId = task.getResult().getDocuments().get(0).getId();
                        updateConversion(messageText);
                    } else {
                        // Verificar en la otra dirección
                        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                                .get()
                                .addOnCompleteListener(task2 -> {
                                    if (task2.isSuccessful() && task2.getResult() != null && task2.getResult().size() > 0) {
                                        conversionId = task2.getResult().getDocuments().get(0).getId();
                                        updateConversion(messageText);
                                    } else {
                                        // Si no existe conversación en ambas direcciones, creamos una nueva
                                        createNewConversation(messageText);
                                    }
                                });
                    }
                });
    }
    private void createNewConversation(String messageText) {
        HashMap<String, Object> conversion = new HashMap<>();
        conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
        conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
        conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
        conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
        conversion.put(Constants.KEY_LAST_MESSAGE, messageText);
        conversion.put(Constants.KEY_TIMESTAMP, new Date());

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }


    private void checkForConversationRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (receiverUser != null && receiverUser.id != null) {
            listenAvailabilityOfReceiver();
        } else {
            showToast("No se pudo cargar la información del receptor.");
        }
    }

    private void listenAvailabilityOfReceiver() {
        if (receiverUser != null && receiverUser.id != null) {
            database.collection(Constants.KEY_COLLECTION_USERS)
                    .document(receiverUser.id)
                    .addSnapshotListener(ChatActivity.this, (value, error) -> {
                        if (error != null) {
                            return;
                        }
                        if (value != null) {
                            if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                                int availability = value.getLong(Constants.KEY_AVAILABILITY).intValue();
                                isReceiverAvailable = availability == 1;
                            }
                            receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                            if (receiverUser.token == null) {
                                receiverUser.image = value.getString(Constants.KEY_IMAGE);
                                if (receiverUser.image != null) {
                                    chatAdapter.setReceiverProfileImage(getBitmapFormEncodedString(receiverUser.image));
                                    chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                                }
                            }
                        }
                        if (isReceiverAvailable) {
                            binding.textAvailability.setVisibility(View.VISIBLE);
                        } else {
                            binding.textAvailability.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                        Constants.getRemoteMsgHeaders(), messageBody)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            try {
                                if (response.body() != null) {
                                    JSONObject responseJson = new JSONObject(response.body());
                                    JSONArray results = responseJson.getJSONArray("results");
                                    if (responseJson.getInt("failure") == 1) {
                                        JSONObject error = results.getJSONObject(0);
                                        showToast(error.getString("error"));
                                        return;
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            showToast("Notificación enviada");
                        } else {
                            showToast("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        showToast(t.getMessage());
                    }
                });
    }
}
