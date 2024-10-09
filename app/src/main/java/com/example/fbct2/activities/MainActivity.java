package com.example.fbct2.activities;

import static android.util.Base64.decode;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;  // Import this for Toast

import androidx.appcompat.app.AppCompatActivity;

import com.example.fbct2.R;
import com.example.fbct2.adapters.RecentConversationsAdapter;
import com.example.fbct2.databinding.ActivityMainBinding;
import com.example.fbct2.listeners.ConversionListener;
import com.example.fbct2.models.ChatMessage;
import com.example.fbct2.models.User;
import com.example.fbct2.utilities.Constants;
import com.example.fbct2.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.material.snackbar.Snackbar;  // Import for Snackbar

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends BaseActivity implements ConversionListener {
    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter conversationsAdapter;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenerConversations();
    }

    private void init() {
        conversations = new ArrayList<>();
        conversationsAdapter = new RecentConversationsAdapter(conversations, this, getApplicationContext());
        binding.conversationRecyclerView.setAdapter(conversationsAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.imageSignOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), UsersActivity.class)));
    }

    private void loadUserDetails() {
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));
        byte[] bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }

    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }

    private void listenerConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }
//funciona
private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
    if (error != null) {
        return;
    }
    if (value != null) {
        for (DocumentChange documentChange : value.getDocumentChanges()) {
            if (documentChange.getType() == DocumentChange.Type.ADDED || documentChange.getType() == DocumentChange.Type.MODIFIED) {
                ChatMessage chatMessage = new ChatMessage();
                String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);

                chatMessage.senderId = senderId;
                chatMessage.receiverId = receiverId;
                chatMessage.message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                chatMessage.conversionId = documentChange.getDocument().getId();

                if (senderId.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {
                    chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                    chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                } else {
                    chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                    chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                }

                // Verifica si ya existe una conversación con el mismo usuario
                boolean isConversationExist = false;
                for (ChatMessage conversation : conversations) {
                    if (conversation.senderId.equals(chatMessage.senderId) && conversation.receiverId.equals(chatMessage.receiverId)) {
                        // Actualiza la conversación existente
                        conversation.message = chatMessage.message;
                        conversation.dateObject = chatMessage.dateObject;
                        isConversationExist = true;
                        break;
                    }
                }

                // Solo agrega la conversación si no existe
                if (!isConversationExist) {
                    conversations.add(chatMessage);
                }
            }
        }
        Collections.sort(conversations, (obj1, obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
        conversationsAdapter.notifyDataSetChanged();
        binding.conversationRecyclerView.smoothScrollToPosition(0);
        binding.conversationRecyclerView.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
    }
};



    private void updateToken(String token) {
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS)
                        .document(preferenceManager.getString(Constants.KEY_USER_ID));
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast("Unable to send token"));
    }

    private void signOut() {
        showToast("Signing out...");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS)
                        .document(preferenceManager.getString(Constants.KEY_USER_ID));
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.clear();
                    startActivity(new Intent(getApplicationContext(), SingInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> showToast("Unable to sign out"));
    }

    private void showToast(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }
    private void getRecentConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        List<ChatMessage> recentMessages = new ArrayList<>();
                        for (DocumentSnapshot document : value.getDocuments()) {
                            ChatMessage chatMessage = new ChatMessage();
                            chatMessage.conversionId = document.getString(Constants.KEY_RECEIVER_ID);
                            chatMessage.conversionName = document.getString(Constants.KEY_RECEIVER_NAME);
                            chatMessage.conversionImage = document.getString(Constants.KEY_RECEIVER_IMAGE);
                            chatMessage.message = document.getString(Constants.KEY_LAST_MESSAGE);
                            recentMessages.add(chatMessage);
                        }

                        if (recentMessages.size() > 0) {
                            conversations.clear();  // Limpiar lista de conversaciones previas
                            conversations.addAll(recentMessages);  // Añadir conversaciones recientes
                            conversationsAdapter.notifyDataSetChanged();  // Actualizar el adaptador
                            binding.conversationRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            binding.textNoConversations.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

}
