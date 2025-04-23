package org.lqzs.sorene;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.Timer;
import java.util.TimerTask;

import org.lqzs.sorene.io.AverageRateCounter;
import org.lqzs.sorene.io.BufferPool;
import org.lqzs.sorene.io.Channel;
import org.lqzs.sorene.io.DirectoryReader;
import org.lqzs.sorene.security.FileEncryption;
import org.lqzs.sorene.security.KeyExchange;

import static org.lqzs.sorene.Sorene.IPTOS_THROUGHPUT;
import static org.lqzs.sorene.Sorene.LOG_TAG;
import static org.lqzs.sorene.Sorene.TCP_PORT;

public class SendService extends TransferService {
	private String host;
	private String[] files = null;
	private KeyExchange keyExchange;
	private FileEncryption fileEncryption;
	private byte[] encryptionKey;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "SendService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		initNotification(R.string.notification_sending);

		host = intent.getStringExtra("host");
		files = intent.getStringArrayExtra("files");
		Uri data = intent.getData();
		if (host == null || files == null || data == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		{
			StringJoiner sj = new StringJoiner(",");
			for (String file : files) {
				sj.add(file);
			}
			Log.d(LOG_TAG, "SendService: host=" + host +
					" files=" + sj.toString() +
					" data=" + data.toString());
		}
		root = DocumentFile.fromTreeUri(this, data);

		acquireLocks();
		thread = new TransferThread();
		thread.start();
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		((Sorene) getApplicationContext()).sendService = this;
		postUpdateButton();
		try {
			keyExchange = new KeyExchange();
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG_TAG, "Failed to initialize key exchange", e);
		}
	}

	@Override
	public void onDestroy() {
		showResult();
		((Sorene) getApplicationContext()).sendService = null;
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}

	private class TransferThread extends Thread {
		private final BufferPool bufferPool = new BufferPool(BufferSize);
		Socket socket = null;

		@Override
		public void interrupt() {
			{
				final Socket socket = this.socket;
				if (socket != null) {
					try {
						socket.setSoLinger(true, 0);
						socket.close();
					} catch (IOException e) {
						Log.e(LOG_TAG, "cancel: socket close failed", e);
					}
				}
			}
			super.interrupt();
		}

		@Override
		public void run() {
			try {
				socket = new Socket();
				socket.setPerformancePreferences(0, 0, 1);
				socket.setTrafficClass(IPTOS_THROUGHPUT);
				socket.setSendBufferSize(TcpBufferSize);
				socket.setSoTimeout(30000);
				socket.setSoLinger(true, 30);
				socket.setTcpNoDelay(false);
				socket.connect(new InetSocketAddress(InetAddress.getByName(host), TCP_PORT), 4000);

				// Perform key exchange
				OutputStream out = socket.getOutputStream();
				InputStream in = socket.getInputStream();

				// Send public key
				byte[] publicKey = keyExchange.getPublicKey();
				out.write(ByteBuffer.allocate(4).putInt(publicKey.length).array());
				out.write(publicKey);
				out.flush();

				// Receive peer's public key
				byte[] lengthBytes = new byte[4];
				if (in.read(lengthBytes) != 4) {
					throw new IOException("Failed to read public key length");
				}
				int peerKeyLength = ByteBuffer.wrap(lengthBytes).getInt();
				byte[] peerPublicKey = new byte[peerKeyLength];
				if (in.read(peerPublicKey) != peerKeyLength) {
					throw new IOException("Failed to read public key");
				}

				// Generate shared secret
				encryptionKey = keyExchange.generateSharedSecret(peerPublicKey);
				fileEncryption = new FileEncryption(encryptionKey);

				streamCopy(socket);
			} catch (SocketTimeoutException e) {
				Log.e(LOG_TAG, "socket timeout");
			} catch (IOException e) {
				Log.e(LOG_TAG, "connect failed", e);
			} finally {
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						Log.e(LOG_TAG, "socket close failed", e);
					}
					socket = null;
				}
				handler.post(SendService.this::stop);
			}
		}

		private void streamCopy(Socket socket) {
			final Channel channel = new Channel(8 * 1024 * 1024); // 8MB
			final Progress progress = new Progress();
			final DirectoryReader reader = new DirectoryReader(getContentResolver(), root, files,
					channel, progress, bufferPool);
			reader.start();
			Timer timer = new Timer();
			try (OutputStream out = fileEncryption.getEncryptingOutputStream(socket.getOutputStream())) {
				AverageRateCounter rate = new AverageRateCounter(5);
				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						final Progress p = progress.get();
						String text = p.text;
						if (text != null) {
							text += "\n";
							final int max = channel.getCapacity();
							final int used = max - channel.getAvailable();
							text += String.format(
									Locale.getDefault(),
									getResources().getString(R.string.buffer_indicator),
									Sorene.formatSize(used),
									Sorene.formatSize(max));
						} else {
							text = getResources().getString(R.string.notification_finishing);
						}
						final String contentText = text;
						final boolean indeterminate = p.max == 0;
						final int max, now;
						if (indeterminate) {
							max = 0;
							now = 0;
						} else {
							max = 1000;
							now = (int) (p.now * 1000 / p.max);
						}
						handler.post(() -> {
							if (builder != null && notificationManager != null) {
								builder.setContentText(contentText)
										.setStyle(new Notification.BigTextStyle().bigText(contentText))
										.setProgress(max, now, indeterminate)
										.setSubText(Sorene.formatSize(rate.rate()) + "/s");
								notificationManager.notify(startId, builder.build());
							}
						});
					}
				}, 1000, 1000);
				while (true) {
					final ByteBuffer packet = channel.read();
					if (packet == null) {
						break;
					}
					rate.increase(packet.limit());
					out.write(packet.array(), packet.arrayOffset() + packet.position(), packet.remaining());
					bufferPool.push(packet);
				}
				out.flush();
				reader.join();
				result = reader.isSuccess();
				Log.d(LOG_TAG, "SendService finished normally");
			} catch (InterruptedException e) {
				Log.d(LOG_TAG, "SendService interrupted");
			} catch (IOException e) {
				Log.e(LOG_TAG, "SendService", e);
			} finally {
				timer.cancel();
				reader.interrupt();
			}
		}
	}
}
