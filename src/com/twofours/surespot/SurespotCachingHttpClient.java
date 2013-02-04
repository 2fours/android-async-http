package com.twofours.surespot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.client.CredentialsProvider;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheStorage;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateCallback;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateException;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;
import ch.boye.httpclientandroidlib.impl.client.cache.CacheConfig;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;

import com.jakewharton.DiskLruCache;
import com.jakewharton.DiskLruCache.Snapshot;
import com.loopj.android.http.RetryHandler;
import com.twofours.surespot.socketio.WebClientDevWrapper;

public class SurespotCachingHttpClient extends CachingHttpClient {
	private AbstractHttpClient mAbstractHttpClient;
	private static SurespotHttpCacheStorage mCacheStorage;

	
	private static SurespotCachingHttpClient mInstance = null;

	public SurespotCachingHttpClient(Context context, CachingHttpClient diskCacheClient, AbstractHttpClient defaultHttpClient,
			SurespotHttpCacheStorage surespotHttpCacheStorage) {
		super(diskCacheClient, getMemoryCacheConfig());
		// log.enableDebug(true);
		// log.enableError(true);
		// log.enableInfo(true);
		// log.enableTrace(true);
		// log.enableWarn(true);

		mAbstractHttpClient = defaultHttpClient;
		mCacheStorage = surespotHttpCacheStorage;
//		defaultHttpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BEST_MATCH);

	}
	
	/**
	 * Use disk cache only
	 * 
	 * @param context
	 * @param defaultHttpClient
	 * @throws IOException
	 */
	public SurespotCachingHttpClient(Context context, AbstractHttpClient defaultHttpClient) throws IOException {
		super(defaultHttpClient, getHttpCacheStorage(context), getDiskCacheConfig());
		log.enableDebug(true);
		log.enableError(true);
		log.enableInfo(true);
		log.enableTrace(true);
		log.enableWarn(true);

		mAbstractHttpClient = WebClientDevWrapper.wrapClient(defaultHttpClient);		
	}

	private static HttpCacheStorage getHttpCacheStorage(Context context) throws IOException {
		if (mCacheStorage == null) {
			mCacheStorage = new SurespotHttpCacheStorage(context);
		}
		return mCacheStorage;
	}

	/**
	 * singleton - TODO dependency injection
	 * 
	 * @param context
	 * @param abstractClient
	 * @return
	 * @throws IOException
	 */
	public static SurespotCachingHttpClient createSurespotCachingHttpClient(Context context, AbstractHttpClient abstractClient)
			throws IOException {
		if (mInstance == null) {

			SurespotHttpCacheStorage storage = new SurespotHttpCacheStorage(context);

			CachingHttpClient diskCacheClient = new CachingHttpClient(abstractClient, storage, getDiskCacheConfig());

			// diskCacheClient.log.enableDebug(true);
			// diskCacheClient.log.enableError(true);
			// diskCacheClient.log.enableInfo(true);
			// diskCacheClient.log.enableTrace(true);
			// diskCacheClient.log.enableWarn(true);

			SurespotCachingHttpClient client = new SurespotCachingHttpClient(context, diskCacheClient, abstractClient, storage);
			mInstance = client;
		}
		return mInstance;
	}

	public static SurespotCachingHttpClient createSurespotDiskCachingHttpClient(Context context, AbstractHttpClient abstractClient)
			throws IOException {
		if (mInstance == null) {
			SurespotCachingHttpClient client = new SurespotCachingHttpClient(context, abstractClient);
			mInstance = client;
		}
		return mInstance;
	}

	private static String generateKey(String key) {
		return md5(key);
	}
	
	public static String md5(String s) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(s.getBytes());
			byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < messageDigest.length; i++)
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			return hexString.toString();

		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}


	public static class SurespotHttpCacheStorage implements HttpCacheStorage {
		private static final String DISK_CACHE_SUBDIR = "http";
		private static final String TAG = "SurespotHttpCacheStorage";
		private com.jakewharton.DiskLruCache mCache;
		private File mCacheDir;

		public SurespotHttpCacheStorage(Context context) throws IOException {

			mCacheDir = getDiskCacheDir(context, DISK_CACHE_SUBDIR);

			Log.v(TAG, "storage cache dir: " + mCacheDir);

			mCache = DiskLruCache.open(mCacheDir, 200, 1, Integer.MAX_VALUE);

		}

		@Override
		public HttpCacheEntry getEntry(String arg0) throws IOException {
			HttpCacheEntry entry = null;
			try {
				Snapshot snapshot = null;

				String key = generateKey(arg0);
				snapshot = mCache.get(key);

				if (snapshot == null) {
					return null;
				}
				InputStream is = snapshot.getInputStream(0);
				ObjectInputStream ois = new ObjectInputStream(is);

				entry = (HttpCacheEntry) ois.readObject();
				snapshot.close();
				ois.close();
			}
			catch (Exception e) {
				throw new IOException("Error retrieving cache entry: " + arg0, e);
			}

			return entry;
		}

		@Override
		public void putEntry(String key, HttpCacheEntry entry) throws IOException {
			try {
				DiskLruCache.Editor edit = mCache.edit(generateKey(key));

				OutputStream outputStream = edit.newOutputStream(0);
				ObjectOutputStream os = new ObjectOutputStream(outputStream);
				os.writeObject(entry);
				os.close();

				edit.commit();
			}
			catch (Exception e) {
				e.printStackTrace();
			}

		}

		@Override
		public void removeEntry(String arg0) throws IOException {
			mCache.remove(generateKey(arg0));
		}

		@Override
		public void updateEntry(String arg0, HttpCacheUpdateCallback arg1) throws IOException, HttpCacheUpdateException {
			try {
				String key = generateKey(arg0);
				putEntry(generateKey(key), arg1.update(getEntry(key)));
			}
			catch (Exception e) {
				e.printStackTrace();
			}

		}

		/**
		 * Removes all disk cache entries from the application cache directory in the uniqueName sub-directory.
		 * 
		 * @param context
		 *            The context to use
		 * @param uniqueName
		 *            A unique cache directory name to append to the app cache directory
		 */
		public void clearCache() {

			clearCache(mCacheDir);
		}
		
		public void close() {
			try {
				mCache.flush();
			//	mCache.close();
			}
			catch (IOException e) {
				Log.w(TAG, "close",e);
			}
		}

		/**
		 * Removes all disk cache entries from the given directory. This should not be called directly, call
		 * {@link DiskLruCache#clearCache(Context, String)} or {@link DiskLruCache#clearCache()} instead.
		 * 
		 * @param cacheDir
		 *            The directory to remove the cache files from
		 */
		private void clearCache(File cacheDir) {
			final File[] files = cacheDir.listFiles();
			for (int i = 0; i < files.length; i++) {
				files[i].delete();
			}
		}

		/**
		 * Get a usable cache directory (external if available, internal otherwise).
		 * 
		 * @param context
		 *            The context to use
		 * @param uniqueName
		 *            A unique directory name to append to the cache dir
		 * @return The cache dir
		 */
		public File getDiskCacheDir(Context context, String uniqueName) {

			// Check if media is mounted or storage is built-in, if so, try and use external cache dir
			// otherwise use internal cache dir
			String cachePath = null;

			// see if we can write to the "external" storage
			if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED || !isExternalStorageRemovable()) {
				cachePath = getExternalCacheDir(context).getPath();
			}

			if (cachePath != null) {
				File cacheDir = new File(cachePath + File.separator + uniqueName);
				if (cacheDir.canWrite()) {
					return cacheDir;
				}

			}

			return new File(context.getCacheDir().getPath() + File.separator + uniqueName);

		}

		/**
		 * Check if external storage is built-in or removable.
		 * 
		 * @return True if external storage is removable (like an SD card), false otherwise.
		 */
		@SuppressLint("NewApi")
		public boolean isExternalStorageRemovable() {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				return Environment.isExternalStorageRemovable();
			}
			return true;
		}

		/**
		 * Get the external app cache directory.
		 * 
		 * @param context
		 *            The context to use
		 * @return The external cache dir
		 */
		@SuppressLint("NewApi")
		public File getExternalCacheDir(Context context) {
			File cacheDir = null;
			if (hasExternalCacheDir()) {
				cacheDir = context.getExternalCacheDir();
			}

			if (cacheDir == null) {
				// Before Froyo we need to construct the external cache dir ourselves
				final String sCacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
				cacheDir = new File(Environment.getExternalStorageDirectory().getPath() + sCacheDir);
			}
			return cacheDir;
		}

		/**
		 * Check if OS version has built-in external cache dir method.
		 * 
		 * @return
		 */
		public boolean hasExternalCacheDir() {
			return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
		}

	}

	public void clearCache() {
		mCacheStorage.clearCache();
	}
	
	private static CacheConfig getMemoryCacheConfig() {

		CacheConfig cacheConfig = new CacheConfig();
		cacheConfig.setMaxCacheEntries(50);
		cacheConfig.setMaxObjectSizeBytes(250000);
		return cacheConfig;
	}

	public static CacheConfig getDiskCacheConfig() {

		CacheConfig cacheConfig = new CacheConfig();
		cacheConfig.setMaxCacheEntries(200);
		cacheConfig.setMaxObjectSizeBytes(250000);
		return cacheConfig;
	}

	public HttpRequestRetryHandler getHttpRequestRetryHandler() {
		return mAbstractHttpClient.getHttpRequestRetryHandler();
	}

	public CredentialsProvider getCredentialsProvider() {
		return mAbstractHttpClient.getCredentialsProvider();
	}

	public void addRequestInterceptor(HttpRequestInterceptor httpRequestInterceptor) {
		mAbstractHttpClient.addRequestInterceptor(httpRequestInterceptor);
	}

	public void addResponseInterceptor(HttpResponseInterceptor httpResponseInterceptor) {
		mAbstractHttpClient.addResponseInterceptor(httpResponseInterceptor);

	}

	public void setHttpRequestRetryHandler(RetryHandler retryHandler) {
		mAbstractHttpClient.setHttpRequestRetryHandler(retryHandler);

	}

	@Override
	public boolean isSharedCache() {
		return true;
	}

}
