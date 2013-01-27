package com.twofours.surespot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URLEncoder;

import android.content.Context;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.client.CredentialsProvider;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheStorage;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateCallback;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateException;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.client.cache.CacheConfig;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;

import com.jakewharton.DiskLruCache;
import com.jakewharton.DiskLruCache.Snapshot;
import com.loopj.android.http.RetryHandler;

public class SurespotCachingHttpClient extends CachingHttpClient {
	private AbstractHttpClient mAbstractHttpClient;

	private static final String DISK_CACHE_SUBDIR = "http";
	private static SurespotCachingHttpClient mInstance = null;

	public SurespotCachingHttpClient(Context context, CachingHttpClient diskCacheClient, AbstractHttpClient defaultHttpClient) {
		super(diskCacheClient, getMemoryCacheConfig());
//		log.enableDebug(true);
//		log.enableError(true);
//		log.enableInfo(true);
//		log.enableTrace(true);
//		log.enableWarn(true);


		mAbstractHttpClient = defaultHttpClient;

	}
	

	/**
	 * Use disk cache only
	 * @param context
	 * @param defaultHttpClient
	 */
	public SurespotCachingHttpClient(Context context,  AbstractHttpClient defaultHttpClient) {
		super(defaultHttpClient, new SurespotHttpCacheStorage(new File(context
				.getCacheDir().getPath() + File.pathSeparator + DISK_CACHE_SUBDIR)), getDiskCacheConfig());
		log.enableDebug(true);
		log.enableError(true);
		log.enableInfo(true);
		log.enableTrace(true);
		log.enableWarn(true);
		
		


		mAbstractHttpClient = defaultHttpClient;

	}

	/**
	 * singleton - TODO dependency injection
	 * @param context
	 * @param abstractClient
	 * @return
	 */
	public static SurespotCachingHttpClient createSurespotCachingHttpClient(Context context, AbstractHttpClient abstractClient) {
		if (mInstance == null) {

			CachingHttpClient diskCacheClient = new CachingHttpClient(abstractClient, new SurespotHttpCacheStorage(new File(context
					.getCacheDir().getPath() + File.pathSeparator + DISK_CACHE_SUBDIR)), getDiskCacheConfig());
			
//			diskCacheClient.log.enableDebug(true);
//			diskCacheClient.log.enableError(true);
//			diskCacheClient.log.enableInfo(true);
//			diskCacheClient.log.enableTrace(true);
//			diskCacheClient.log.enableWarn(true);

			SurespotCachingHttpClient client = new SurespotCachingHttpClient(context, diskCacheClient, abstractClient);
			mInstance = client;
		}
		return mInstance;
	}
	
	public static SurespotCachingHttpClient createSurespotDiskCachingHttpClient(Context context, AbstractHttpClient abstractClient) {
		if (mInstance == null) {
			SurespotCachingHttpClient client = new SurespotCachingHttpClient(context, abstractClient);
			mInstance = client;
		}
		return mInstance;
	}

	private static String generateKey(String key) {
		return key.replaceAll("[^a-zA-Z0-9_-]", "");
	}

	public static class SurespotHttpCacheStorage implements HttpCacheStorage {
		private static final String TAG = "SurespotHttpCacheStorage";
		private com.jakewharton.DiskLruCache mCache;

		public SurespotHttpCacheStorage(File cacheDir) {
			try {
				Log.v(TAG, "storage cache dir: " + cacheDir);

				mCache = DiskLruCache.open(cacheDir, 100, 1, Integer.MAX_VALUE);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
				ois.close();
			} catch (Exception e) {
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
			} catch (Exception e) {
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
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

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

}
