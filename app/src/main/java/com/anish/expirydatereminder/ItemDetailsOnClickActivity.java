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
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.anish.expirydatereminder.db.DateFormatDatabase;
import com.anish.expirydatereminder.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class ItemDetailsOnClickActivity extends AppCompatActivity {
    private TextView itemName, expiresOn, categoryName;
    private Button backBtn, addPicButton, deletePicButton;
    private ImageView itemImage;
    private String item_name, category_name;
    private int month, year, date;

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
        month = intent.getIntExtra("month", 0);
        year = intent.getIntExtra("year", 0);
        category_name = intent.getStringExtra("category");
        date = intent.getIntExtra("date", 0);

        itemName.setText("Item: " + item_name);

        String m = month + "", d = date + "";
        if (month < 10) {
            m = "0" + month;
        }
        if (date < 10) {
            d = "0" + date;
        }

        DateFormatDatabase dateFormatDatabase = new DateFormatDatabase(getApplicationContext());
        if (dateFormatDatabase.getCurrentFormat() == 1) {
            expiresOn.setText("Expires On: " + m + "/" + d + "/" + year);
        } else {
            expiresOn.setText("Expires On: " + d + "/" + m + "/" + year);
        }
        categoryName.setText("Category: " + category_name);

        itemImage = findViewById(R.id.imageView);
        itemImage.setImageURI(FileUtils.getFileURI(getFileNameText()));

        backBtn.setOnClickListener(view -> finish());

        addPicButton.setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            activityResultLauncher.launch(takePictureIntent);
        });
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                assert result.getData() != null;
            } catch (AssertionError e) {
                Log.d("AssertionError", "no image saved!");
                Toast.makeText(this, "Action was disrupted, no image saved. Try again!", Toast.LENGTH_SHORT).show();
                return;
            }

            Bundle extras = result.getData().getExtras();
            if (extras != null) {
                Uri imageUri;
                Bitmap imageBitmap = (Bitmap) extras.get("data");

                WeakReference<Bitmap> result1 = new WeakReference<>(Bitmap.createScaledBitmap(imageBitmap,
                        imageBitmap.getWidth(), imageBitmap.getHeight(), false).copy(Bitmap.Config.RGB_565, true));

                Bitmap bm = result1.get();
                imageUri = saveImage(bm, ItemDetailsOnClickActivity.this);
                itemImage.setImageURI(imageUri);
                System.out.println("///////////////////\nImage uri = \n" + imageUri + "\n\n/////////////////");
                Toast.makeText(this, "Image saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Log.d("Image Save?", "Not saved, as user cancelled the image capture event");
                Toast.makeText(this, "No image saved.", Toast.LENGTH_SHORT).show();
            }
        });

        deletePicButton.setOnClickListener(view -> deleteImage());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private Uri saveImage(Bitmap image, Context context) {

        File imagesFolder = new File(context.getCacheDir(), "images");
        Uri uri = null;
        try {
            imagesFolder.mkdirs();
            File file = new File(imagesFolder, getFileNameText());
            FileOutputStream stream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();

            uri = FileProvider.getUriForFile(context.getApplicationContext(), "com.anish.expirydatereminder" + ".provider", file);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }

    @NonNull
    private String getFileNameText() {
        return FileUtils.getImageFileName(item_name, date, month, year, category_name);
    }

    private void deleteImage() {
        Uri uri = FileUtils.getFileURI(getFileNameText());
        ContentResolver contentResolver = getContentResolver();
        contentResolver.delete(uri, null, null);
        itemImage.setImageBitmap(null);
    }
}