package com.example.fbct2.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fbct2.databinding.ItemContainerRecentConversionBinding;
import com.example.fbct2.listeners.ConversionListener;
import com.example.fbct2.models.ChatMessage;
import com.example.fbct2.models.User;
import com.example.fbct2.utilities.Constants;
import com.example.fbct2.utilities.PreferenceManager;

import java.util.List;

public class RecentConversationsAdapter extends RecyclerView.Adapter<RecentConversationsAdapter.ConversionViewHolder> {

    private final List<ChatMessage> chatMessages;
    private final ConversionListener conversionListener;
    private final PreferenceManager preferenceManager;

    public RecentConversationsAdapter(List<ChatMessage> chatMessages, ConversionListener conversionListener, Context context) {
        this.chatMessages = chatMessages;
        this.conversionListener = conversionListener;
        this.preferenceManager = new PreferenceManager(context);  // Inicializa PreferenceManager
    }

    @NonNull
    @Override
    public ConversionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversionViewHolder(ItemContainerRecentConversionBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversionViewHolder holder, int position) {
        holder.setData(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    class ConversionViewHolder extends RecyclerView.ViewHolder {
        ItemContainerRecentConversionBinding binding;

        ConversionViewHolder(ItemContainerRecentConversionBinding itemContainerRecentConversionBinding) {
            super(itemContainerRecentConversionBinding.getRoot());
            binding = itemContainerRecentConversionBinding;
        }

        void setData(ChatMessage chatMessage) {
            Bitmap conversionImage = getConversionImage(chatMessage.conversionImage);
            if (conversionImage != null) {
                binding.imageProfile.setImageBitmap(conversionImage); // Establece la imagen si está disponible
            } else {
                binding.imageProfile.setImageResource(android.R.color.transparent); // Establece imagen transparente si no está disponible
            }

            binding.textName.setText(chatMessage.conversionName);  // Asegúrate de que se establece el nombre correctamente
            binding.textRecentMessage.setText(chatMessage.message);

            // Asigna el click listener para abrir la conversación
            binding.getRoot().setOnClickListener(v -> {
                User user = new User();
                user.id = chatMessage.senderId.equals(preferenceManager.getString(Constants.KEY_USER_ID)) ? chatMessage.receiverId : chatMessage.senderId;
                user.name = chatMessage.conversionName;
                user.image = chatMessage.conversionImage;
                conversionListener.onConversionClicked(user);  // Asegúrate de enviar el usuario correcto
            });
        }

    }

    private Bitmap getConversionImage(String encodedImage) {
        if (encodedImage == null || encodedImage.isEmpty()) {
            return null;
        }
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
