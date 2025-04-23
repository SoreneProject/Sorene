package org.lqzs.sorene;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import static org.lqzs.sorene.Sorene.LOG_TAG;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
	private static final int REQUEST_SEND = 1;
	private static final int REQUEST_RECEIVE = 2;
	private static final int REQUEST_CHOOSE = 3;
	private static final int CAMERA_PERMISSION_REQUEST = 100;
	final Handler handler = new Handler(Looper.myLooper());
	private Uri uri;
	private String[] selectedFiles;
	private Button sendButton, receiveButton;
	private static final Pattern IPV4_PATTERN = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

	@Override
	protected void onResume() {
		super.onResume();
		updateButtons();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private boolean checkCameraPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
					!= PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[]{android.Manifest.permission.CAMERA},
						CAMERA_PERMISSION_REQUEST);
				return false;
			}
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == CAMERA_PERMISSION_REQUEST) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startQrScanner();
			} else {
				Toast.makeText(this, "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show();
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	private void startQrScanner() {
		try {
			IntentIntegrator integrator = new IntentIntegrator(this);
			integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
			integrator.setPrompt(getString(R.string.scan_qr_code));
			integrator.setCameraId(0);
			integrator.setBeepEnabled(true);
			integrator.setBarcodeImageEnabled(true);
			integrator.setOrientationLocked(true);
			integrator.initiateScan();
		} catch (Exception e) {
			Toast.makeText(this, "Error starting QR scanner: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private int getIpPriority(String ip) {
		if (!IPV4_PATTERN.matcher(ip).matches()) {
			return -1;
		}
		
		String[] parts = ip.split("\\.");
		int firstOctet = Integer.parseInt(parts[0]);
		int secondOctet = Integer.parseInt(parts[1]);
		
		// 192.168.0.0/16 - highest priority
		if (firstOctet == 192 && secondOctet == 168) {
			return 3;
		}
		
		// 172.16.0.0/12 - medium priority
		if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
			return 2;
		}
		
		// 10.0.0.0/8 - lowest priority
		if (firstOctet == 10) {
			return 1;
		}
		
		return 0;
	}

	private String getLocalIpAddress() {
		String bestIp = null;
		int bestPriority = -1;
		
		try {
			for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}
				
				for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
					String ip = address.getHostAddress();
					int priority = getIpPriority(ip);
					
					if (priority > bestPriority) {
						bestPriority = priority;
						bestIp = ip;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return bestIp;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent result) {
		// Check for QR code scan result
		IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, result);
		if (scanResult != null) {
			if (scanResult.getContents() == null) {
				Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
			} else {
				String qrContent = scanResult.getContents();
				Toast.makeText(this, 
					getResources().getString(R.string.qr_code_scan_result, qrContent), 
					Toast.LENGTH_LONG).show();
					
				if (qrContent != null && qrContent.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
					if (uri != null && selectedFiles != null) {
						Intent intent = new Intent(this, SendService.class);
						intent.setData(uri);
						intent.putExtra("host", qrContent);
						intent.putExtra("files", selectedFiles);
						startForegroundServiceCompat(intent);
					}
				} else {
					Toast.makeText(this, "Invalid IP address in QR code", Toast.LENGTH_SHORT).show();
				}
			}
			return;
		}
		
		// Handle other activity results
		if (resultCode != RESULT_OK) {
			return;
		}
		switch (requestCode) {
		case REQUEST_SEND: {
			uri = result.getData();
			if (uri != null) {
				Intent intent = new Intent(this, ChooseActivity.class);
				intent.setData(uri);
				startActivityForResult(intent, REQUEST_CHOOSE);
			}
		}
		break;
		case REQUEST_RECEIVE: {
			uri = result.getData();
			if (uri != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setData(uri);
				startForegroundServiceCompat(intent);
				Toast.makeText(MainActivity.this, R.string.start_receive_service, Toast.LENGTH_SHORT).show();
				receiveButton.setText(R.string.receive_cancel_button);

				// Get local IP address and show QR code
				String ipAddress = getLocalIpAddress();
				if (ipAddress != null) {
					Intent qrIntent = new Intent(this, QrCodeActivity.class);
					qrIntent.putExtra(QrCodeActivity.EXTRA_IP_ADDRESS, ipAddress);
					startActivity(qrIntent);
				} else {
					Toast.makeText(this, "Could not determine IP address", Toast.LENGTH_SHORT).show();
				}
			}
		}
		break;
		case REQUEST_CHOOSE: {
			selectedFiles = result.getStringArrayExtra("files");
			if (selectedFiles == null) {
				break;
			}
			if (checkCameraPermission()) {
				startQrScanner();
			}
		}
		break;
		}
	}

	void updateButtons() {
		if (((Sorene) getApplicationContext()).receiveService == null) {
			receiveButton.setText(R.string.receive_button);
		} else {
			receiveButton.setText(R.string.receive_cancel_button);
		}
		if (((Sorene) getApplicationContext()).sendService == null) {
			sendButton.setText(R.string.send_button);
		} else {
			sendButton.setText(R.string.send_cancel_button);
		}
	}

	@Override
	protected void onDestroy() {
		((Sorene) getApplicationContext()).mainActivity = null;
		super.onDestroy();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final Sorene app = (Sorene) getApplicationContext();
		app.mainActivity = this;

		receiveButton = findViewById(R.id.ReceiveButton);
		receiveButton.setOnClickListener((View v) -> {
			if (app.receiveService != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setAction("cancel");
				startForegroundServiceCompat(intent);
			} else {
				Toast.makeText(MainActivity.this, R.string.choose_storage_directory, Toast.LENGTH_SHORT).show();
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(intent, REQUEST_RECEIVE);
			}
		});

		sendButton = findViewById(R.id.SendButton);
		sendButton.setOnClickListener((View v) -> {
			Toast.makeText(MainActivity.this, R.string.choose_send_directory, Toast.LENGTH_SHORT).show();
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			startActivityForResult(intent, REQUEST_SEND);
		});
	}

	private void startForegroundServiceCompat(Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}
}
