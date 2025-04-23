package org.lqzs.sorene;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QrCodeActivity extends Activity {
    public static final String EXTRA_IP_ADDRESS = "ip_address";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code);

        String ipAddress = getIntent().getStringExtra(EXTRA_IP_ADDRESS);
        if (ipAddress == null) {
            finish();
            return;
        }

        ImageView qrCodeImageView = findViewById(R.id.qr_code_image);
        TextView ipAddressTextView = findViewById(R.id.ip_address_text);

        ipAddressTextView.setText(ipAddress);

        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(ipAddress, BarcodeFormat.QR_CODE, 512, 512);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            qrCodeImageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
} 