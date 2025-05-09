package org.lqzs.sorene;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.lqzs.sorene.io.AverageRateCounter;
import org.lqzs.sorene.io.BufferPool;
import org.lqzs.sorene.io.Channel;
import org.lqzs.sorene.io.DirectoryWriter;
import org.lqzs.sorene.security.FileEncryption;
import org.lqzs.sorene.security.KeyExchange;

import static org.lqzs.sorene.Sorene.IPTOS_THROUGHPUT;
import static org.lqzs.sorene.Sorene.LOG_TAG;

public class ReceiveService extends TransferService {
	private KeyExchange keyExchange;
	private FileEncryption fileEncryption;
	private byte[] encryptionKey;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "ReceiveService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		initNotification(R.string.notification_receiving);

		Uri data = intent.getData();
		if (data == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		root = DocumentFile.fromTreeUri(this, data);

		acquireLocks();
		thread = new ReceiveThread();
		thread.start();

		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		final Sorene app = (Sorene) getApplicationContext();
		app.receiveService = this;
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
		((Sorene) getApplicationContext()).receiveService = null;
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}

	private class ReceiveThread extends Thread {
		private final BufferPool bufferPool = new BufferPool(BufferSize);
		ServerSocket listener = null;
		Socket socket = null;

		@Override
		public void interrupt() {
			{
				final ServerSocket listener = this.listener;
				if (listener != null) {
					try {
						listener.close();
					} catch (IOException ignored) {
					}
				}
			}
			{
				final Socket socket = this.socket;
				if (socket != null) {
					try {
						socket.setSoLinger(true, 0);
						socket.close();
					} catch (IOException ignored) {
					}
				}
			}
			super.interrupt();
		}

		@Override
		public void run() {
			try {
				try {
					listener = new ServerSocket(Sorene.TCP_PORT);
					Log.d(LOG_TAG, "ReceiveService begins to listen");
					listener.setSoTimeout(1000);
					listener.setPerformancePreferences(0, 0, 1);
					listener.setReceiveBufferSize(TcpBufferSize);
					while (!isInterrupted()) {
						try {
							socket = listener.accept();
							break;
						} catch (SocketTimeoutException ignored) {
						}
					}
				} finally {
					listener.close();
					listener = null;
				}
				if (socket == null) {
					return;
				}

				InputStream in = socket.getInputStream();
				OutputStream out = socket.getOutputStream();

				byte[] lengthBytes = new byte[4];
				if (in.read(lengthBytes) != 4) {
					throw new IOException("Failed to read public key length");
				}
				int peerKeyLength = ByteBuffer.wrap(lengthBytes).getInt();
				byte[] peerPublicKey = new byte[peerKeyLength];
				if (in.read(peerPublicKey) != peerKeyLength) {
					throw new IOException("Failed to read public key");
				}

				byte[] publicKey = keyExchange.getPublicKey();
				out.write(ByteBuffer.allocate(4).putInt(publicKey.length).array());
				out.write(publicKey);
				out.flush();

				encryptionKey = keyExchange.generateSharedSecret(peerPublicKey);
				fileEncryption = new FileEncryption(encryptionKey);

				streamCopy(socket);
			} catch (Exception e) {
				Log.e(LOG_TAG, "ReceiveService unexpected exception", e);
			} finally {
				Log.d(LOG_TAG, "ReceiveService closing");
				handler.post(ReceiveService.this::stop);
			}
		}

		private void streamCopy(Socket socket) throws InterruptedException, IOException {
			final int bufferSize = 112 * 1024 * 1024;
			Log.d(LOG_TAG, "receive buffer size: " + Sorene.formatSize(bufferSize));
			final Channel channel = new Channel(bufferSize);
			final Progress progress = new Progress();
			final DirectoryWriter writer = new DirectoryWriter(getContentResolver(), root,
					channel, progress, bufferPool);
			writer.start();
			Timer timer = new Timer();
			try (InputStream in = fileEncryption.getDecryptingInputStream(socket.getInputStream())) {
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
					ByteBuffer packet = bufferPool.pop();
					while (packet.remaining() > 0) {
						int read = in.read(packet.array(), packet.arrayOffset() + packet.position(), packet.remaining());
						if (read < 0) {
							break;
						}
						packet.position(packet.position() + read);
					}
					packet.flip();
					if (packet.limit() < 1) {
						bufferPool.push(packet);
						break;
					}
					rate.increase(packet.limit());
					channel.write(packet);
				}
				channel.close();
				writer.join();
				result = writer.isSuccess();
				Log.d(LOG_TAG, "receive thread finished normally");
			} finally {
				timer.cancel();
				writer.interrupt();
			}
		}
	}
}
