package com.example.chat.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import com.example.chat.adapters.ChatAdapter;
import com.example.chat.databinding.ActivityChatBinding;
import com.example.chat.models.ChatMessage;
import com.example.chat.models.User;
import com.example.chat.utilities.Constans;
import com.example.chat.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import android.util.Base64;
import android.view.View;
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
    private String conversionId=null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        laodReceiverDetails();
        init();
        listenMessages();
    }
    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedImage(receiverUser.image),
                preferenceManager.getString(Constans.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }
    private void sendMessage(){
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constans.KEY_SENDER_ID, preferenceManager.getString(Constans.KEY_USER_ID));
        message.put(Constans.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constans.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constans.KEY_TIMESTAMP, new Date());
        database.collection(Constans.KEY_COLLECTION_CHAT).add(message);
        if(conversionId != null){
            updateConversation((binding.inputMessage.getText().toString()));
        }else{
            HashMap<String, Object> conversion =   new HashMap<>();
            conversion.put(Constans.KEY_SENDER_ID, preferenceManager.getString(Constans.KEY_USER_ID));
            conversion.put(Constans.KEY_SENDER_NAME, preferenceManager.getString(Constans.KEY_NAME));
            conversion.put(Constans.KEY_SENDER_IMAGE, preferenceManager.getString(Constans.KEY_IMAGE));
            conversion.put(Constans.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constans.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constans.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constans.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constans.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        binding.inputMessage.setText(null);
    }

    private void listenAvailabilityOfReceiver(){
        database.collection(Constans.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) ->{
             if(error != null){
                 return;
             }
            if(value != null){
                if(value.getLong(Constans.KEY_AVAILABILITY) != null){
                    int availability = Objects.requireNonNull(
                            value.getLong(Constans.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability ==1;
                }
            }
            if(isReceiverAvailable){
                binding.textAvailability.setVisibility(View.VISIBLE);
            }else{
                binding.textAvailability.setVisibility(View.GONE);
            }
        });
    }
    private void listenMessages(){
        database.collection(Constans.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constans.KEY_SENDER_ID, preferenceManager.getString(Constans.KEY_USER_ID))
                .whereEqualTo(Constans.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constans.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constans.KEY_SENDER_ID,receiverUser.id)
                .whereEqualTo(Constans.KEY_RECEIVER_ID, preferenceManager.getString(Constans.KEY_USER_ID))
                .addSnapshotListener(eventListener);

    }
    //Refresh conversation and show news messages
    private final EventListener<QuerySnapshot> eventListener =(value, error) -> {
      if(error != null){
          return;
      }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()){
                if(documentChange.getType() == DocumentChange.Type.ADDED){
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constans.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constans.KEY_RECEIVER_ID);
                    chatMessage.message =  documentChange.getDocument().getString(Constans.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }

            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0){
                chatAdapter.notifyDataSetChanged();
            }else{
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size()-1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if(conversionId == null){
            checkForConversation();
        }
    };

    private Bitmap getBitmapFromEncodedImage(String encodedImage){
        byte[] bytes= Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private void laodReceiverDetails(){
        receiverUser = (User)getIntent().getSerializableExtra(Constans.KEY_USER);
        binding.textName.setText(receiverUser.name);


    }
    private void setListeners(){
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }
    private  String getReadableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }
    private void addConversion(HashMap<String, Object> conversion){
        database.collection(Constans.KEY_COLLECTION_CONVERSTATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference ->  conversionId = documentReference.getId());
    }
    private void updateConversation(String message){
        DocumentReference documentReference =
                database.collection(Constans.KEY_COLLECTION_CONVERSTATIONS).document(conversionId);
        documentReference.update(
                Constans.KEY_LAST_MESSAGE, message,
                Constans.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversation(){
        if(chatMessages.size() != 0){
            checkForConversionRemotely(
                    preferenceManager.getString(Constans.KEY_USER_ID),
                    receiverUser.id

            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constans.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId){
        database.collection(Constans.KEY_COLLECTION_CONVERSTATIONS)
                .whereEqualTo(Constans.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constans.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }


    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
      if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0){
          DocumentSnapshot documentSnapshot =task.getResult().getDocuments().get(0);
          conversionId = documentSnapshot.getId();
      }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}