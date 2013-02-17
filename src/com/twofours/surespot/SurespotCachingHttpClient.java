package com.twofours.surespot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.client.CredentialsProvider;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheStorage;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateCallback;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateException;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;
import ch.boye.httpclientandroidlib.impl.client.cache.CacheConfig;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;

import com.jakewharton.DiskLruCache;
import com.jakewharton.DiskLruCache.Snapshot;
import com.loopj.android.http.RetryHandler;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.WebClientDevWrapper;

public class SurespotCachingHttpClient extends CachingHttpClient {
	private AbstractHttpClient mAbstractHttpClient;
	private static SurespotHttpCacheStorage mCacheStorage;	
	//private static SurespotCachingHttpClient mInstance = null;

	
	/**
	 * Use disk cache only
	 * 
	 * @param context
	 * @param defaultHttpClient
	 * @throws IOException
	 */
	public SurespotCachingHttpClient(Context context, AbstractHttpClient defaultHttpClient, String cacheName) throws IOException {
		super(defaultHttpClient, getHttpCacheStorage(context, cacheName), getDiskCacheConfig());
		log.enableDebug(true);
		log.enableError(true);
		log.enableInfo(true);
		log.enableTrace(true);
		log.enableWarn(true);

		WebClientDevWrapper.wrapClient(defaultHttpClient);
		mAbstractHttpClient = defaultHttpClient;
						
	}

	private static HttpCacheStorage getHttpCacheStorage(Context context, String cacheName) throws IOException {
		if (mCacheStorage == null) {
			mCacheStorage = new SurespotHttpCacheStorage(context, cacheName);
		}
		return mCacheStorage;
	}


	public static SurespotCachingHttpClient createSurespotDiskCachingHttpClient(Context context, AbstractHttpClient abstractClient)
			throws IOException {
			return new SurespotCachingHttpClient(context, abstractClient, "http");
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
		private static final String TAG = "SurespotHttpCacheStorage";
		private com.jakewharton.DiskLruCache mCache;
		private File mCacheDir;

		public SurespotHttpCacheStorage(Context context, String cacheName) throws IOException {

			mCacheDir = FileUtils.getHttpCacheDir(context, cacheName);

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
	
	public void setCookieStore(CookieStore cookieStore) {
		mAbstractHttpClient.setCookieStore(cookieStore);
	}

	@Override
	public boolean isSharedCache() {
		return true;
	}

}
