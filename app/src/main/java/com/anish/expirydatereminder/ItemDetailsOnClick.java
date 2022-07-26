package com.anish.expirydatereminder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class ItemDetailsOnClick extends AppCompatActivity {
    TextView itemName, expiresOn, categoryName;
    Button backBtn, addPicButton, deletePicButton;
    ImageView itemImage;
    String item_name, category_name;
    int month,year,date;

    ActivityResultLauncher<Intent> activityResultLauncher;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details_on_click);

        Objects.requireNonNull(getSupportActionBar()).hide();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        itemName = findViewById(R.id.nameOfItem);
        expiresOn = findViewById(R.id.expiresOn);
        backBtn = findViewById(R.id.backButton);
        addPicButton = findViewById(R.id.addPicture);
        deletePicButton = findViewById(R.id.deletePicture);
        categoryName = findViewById(R.id.category_image_view_in_item_details);

        Intent intent = getIntent();
        item_name = intent.getStringExtra("item name");
        month = intent.getIntExtra("month",0);
        year = intent.getIntExtra("year",0);
        category_name = intent.getStringExtra("category");
        date = intent.getIntExtra("date",0);

        itemName.setText("Item Name - " + item_name);
        expiresOn.setText("Expires On - "+ date+"/"+month+"/"+year + " DD/MM/YYYY");
        categoryName.setText("Category - "+category_name);

        itemImage = findViewById(R.id.imageView);
        itemImage.setRotation(90);

        checkIfImageExistsAlready();


        backBtn.setOnClickListener(view -> {
            finish();
        });

        addPicButton.setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            activityResultLauncher.launch(takePictureIntent);
        });
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            assert result.getData() != null;
            Bundle extras = result.getData().getExtras();
            Uri imageUri;
            Bitmap imageBitmap = (Bitmap) extras.get("data");

            WeakReference<Bitmap> result1 = new WeakReference<>(Bitmap.createScaledBitmap(imageBitmap,
                    imageBitmap.getHeight(),imageBitmap.getWidth(),false).copy(Bitmap.Config.RGB_565,true));

            Bitmap bm = result1.get();
            imageUri = saveImage(bm,ItemDetailsOnClick.this);
            itemImage.setImageURI(imageUri);
            System.out.println("///////////////////\nImage uri = \n" + imageUri + "\n\n/////////////////");
        });

        deletePicButton.setOnClickListener(view -> deleteImage());
    }

    private Uri saveImage(Bitmap image, Context context) {

        File imagesFolder = new File(context.getCacheDir(),"images");
        Uri uri = null;
        try {
            imagesFolder.mkdirs();
            String fileNameText = "anish_" + item_name + "." + month + "." + year + ".jpg";
            File file = new File(imagesFolder,fileNameText);
            FileOutputStream stream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG,100,stream);
            stream.flush();
            stream.close();

            uri = FileProvider.getUriForFile(context.getApplicationContext(),"com.anish.expirydatereminder"+".provider",file);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }

    private void checkIfImageExistsAlready() {
        String fileNameText = "anish_" + item_name + "." + month + "." + year + ".jpg";
        Uri uri = Uri.parse("content://com.anish.expirydatereminder.provider/cache/images/"+fileNameText);
        itemImage.setImageURI(uri);
    }

    private void deleteImage(){
        String fileNameText = "anish_" + item_name + "." + month + "." + year + ".jpg";
        Uri uri = Uri.parse("content://com.anish.expirydatereminder.provider/cache/images/"+fileNameText);
        ContentResolver contentResolver = getContentResolver();
        contentResolver.delete(uri,null,null);
        itemImage.setImageBitmap(null);
    }

    @Override
    public void onBackPressed(){
        finish();
    }
}