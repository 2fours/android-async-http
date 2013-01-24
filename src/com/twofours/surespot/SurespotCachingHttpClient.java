package com.twofours.surespot;

import java.io.IOException;

import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.client.CredentialsProvider;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheStorage;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateCallback;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateException;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.client.cache.CacheConfig;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;

import com.loopj.android.http.RetryHandler;

public class SurespotCachingHttpClient extends CachingHttpClient {
	private DefaultHttpClient mDefaultHttpClient;
	
	public SurespotCachingHttpClient(DefaultHttpClient defaultHttpClient) {
		super(defaultHttpClient, getCacheConfig());
		mDefaultHttpClient = defaultHttpClient;
		

	}
	
	public class SurespotHttpCacheStorage implements HttpCacheStorage {

		@Override
		public HttpCacheEntry getEntry(String arg0) throws IOException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void putEntry(String arg0, HttpCacheEntry arg1) throws IOException {
			// TODO Auto-generated method stub		
		}

		@Override
		public void removeEntry(String arg0) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void updateEntry(String arg0, HttpCacheUpdateCallback arg1) throws IOException, HttpCacheUpdateException {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	private static CacheConfig getCacheConfig() {
		
		
		
		CacheConfig cacheConfig = new CacheConfig();  
		cacheConfig.setMaxCacheEntries(50);
		cacheConfig.setMaxObjectSizeBytes(120000);
		return cacheConfig;
	}

	public HttpRequestRetryHandler getHttpRequestRetryHandler() {
		return mDefaultHttpClient.getHttpRequestRetryHandler();
	}

	public CredentialsProvider getCredentialsProvider() {
		return mDefaultHttpClient.getCredentialsProvider();
	}

	public void addRequestInterceptor(HttpRequestInterceptor httpRequestInterceptor) {
		mDefaultHttpClient.addRequestInterceptor(httpRequestInterceptor);
	}

	public void addResponseInterceptor(HttpResponseInterceptor httpResponseInterceptor) {
		mDefaultHttpClient.addResponseInterceptor(httpResponseInterceptor);

	}

	public void setHttpRequestRetryHandler(RetryHandler retryHandler) {
		mDefaultHttpClient.setHttpRequestRetryHandler(retryHandler);
		
	}

}
