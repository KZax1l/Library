package com.bumptech.glide;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.data.InputStreamRewinder;
import com.bumptech.glide.load.engine.Engine;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.engine.cache.MemoryCache;
import com.bumptech.glide.load.engine.prefill.BitmapPreFiller;
import com.bumptech.glide.load.engine.prefill.PreFillType;
import com.bumptech.glide.load.model.AssetUriLoader;
import com.bumptech.glide.load.model.ByteArrayLoader;
import com.bumptech.glide.load.model.ByteBufferEncoder;
import com.bumptech.glide.load.model.ByteBufferFileLoader;
import com.bumptech.glide.load.model.DataUrlLoader;
import com.bumptech.glide.load.model.FileLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.MediaStoreFileLoader;
import com.bumptech.glide.load.model.ResourceLoader;
import com.bumptech.glide.load.model.StreamEncoder;
import com.bumptech.glide.load.model.StringLoader;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.load.model.UriLoader;
import com.bumptech.glide.load.model.UrlUriLoader;
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader;
import com.bumptech.glide.load.model.stream.HttpUriLoader;
import com.bumptech.glide.load.model.stream.MediaStoreImageThumbLoader;
import com.bumptech.glide.load.model.stream.MediaStoreVideoThumbLoader;
import com.bumptech.glide.load.model.stream.UrlLoader;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableDecoder;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableEncoder;
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder;
import com.bumptech.glide.load.resource.bitmap.ByteBufferBitmapDecoder;
import com.bumptech.glide.load.resource.bitmap.DefaultImageHeaderParser;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder;
import com.bumptech.glide.load.resource.bitmap.VideoBitmapDecoder;
import com.bumptech.glide.load.resource.bytes.ByteBufferRewinder;
import com.bumptech.glide.load.resource.file.FileDecoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawableEncoder;
import com.bumptech.glide.load.resource.gif.GifFrameResourceDecoder;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;
import com.bumptech.glide.load.resource.transcode.BitmapBytesTranscoder;
import com.bumptech.glide.load.resource.transcode.BitmapDrawableTranscoder;
import com.bumptech.glide.load.resource.transcode.GifDrawableBytesTranscoder;
import com.bumptech.glide.manager.ConnectivityMonitorFactory;
import com.bumptech.glide.manager.RequestManagerRetriever;
import com.bumptech.glide.module.GlideModule;
import com.bumptech.glide.module.ManifestParser;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.ImageViewTargetFactory;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.util.Util;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A singleton to present a simple static interface for building requests with
 * {@link RequestBuilder} and maintaining an {@link Engine}, {@link BitmapPool},
 * {@link com.bumptech.glide.load.engine.cache.DiskCache} and {@link MemoryCache}.
 * <p>
 * 一个简单独立的静态接口，通过{@link RequestBuilder}创建请求并维持一个{@link Engine}，
 * {@link BitmapPool}，{@link com.bumptech.glide.load.engine.cache.DiskCache}和{@link MemoryCache}
 * <p>
 * 之所以要实现{@link ComponentCallbacks2}接口，是为了方便管理集成{@code Glide}框架应用的内存
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class Glide implements ComponentCallbacks2 {
    private static final String DEFAULT_DISK_CACHE_DIR = "image_manager_disk_cache";
    private static final String TAG = "Glide";
    private static volatile Glide glide;

    private final Engine engine;
    private final BitmapPool bitmapPool;
    private final MemoryCache memoryCache;
    private final BitmapPreFiller bitmapPreFiller;
    private final GlideContext glideContext;
    private final Registry registry;
    private final ArrayPool arrayPool;
    private final RequestManagerRetriever requestManagerRetriever;
    private final ConnectivityMonitorFactory connectivityMonitorFactory;
    private final List<RequestManager> managers = new ArrayList<>();
    private MemoryCategory memoryCategory = MemoryCategory.NORMAL;

    /**
     * Returns a directory with a default name in the private cache directory of the application to
     * use to store retrieved media and thumbnails.
     * <p>
     * 恢复用默认内存缓存目录存储的缩略图文件
     *
     * @param context A context.
     * @see #getPhotoCacheDir(Context, String)
     */
    @Nullable
    public static File getPhotoCacheDir(Context context) {
        return getPhotoCacheDir(context, DEFAULT_DISK_CACHE_DIR);
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to
     * use to store retrieved media and thumbnails.
     * <p>
     * 恢复用给定文件夹名的内存缓存目录存储的缩略图文件
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see #getPhotoCacheDir(Context)
     */
    @Nullable
    public static File getPhotoCacheDir(Context context, String cacheName) {
        File cacheDir = context.getCacheDir();
        if (cacheDir != null) {
            File result = new File(cacheDir, cacheName);
            if (!result.mkdirs() && (!result.exists() || !result.isDirectory())) {
                // File wasn't able to create a directory, or the result exists but not a directory
                return null;
            }
            return result;
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "default disk cache dir is null");
        }
        return null;
    }

    /**
     * Get the singleton.
     * <p>
     * 返回单例的Glide实例，在这里创建单例时会解析到在{@code AndroidManifest.xml}中注册的
     * {@link GlideModule}
     *
     * @return the singleton
     */
    public static Glide get(Context context) {
        if (glide == null) {
            synchronized (Glide.class) {
                if (glide == null) {
                    initGlide(context);
                }
            }
        }

        return glide;
    }

    @SuppressWarnings("deprecation")
    private static void initGlide(Context context) {
        Context applicationContext = context.getApplicationContext();

        GeneratedRootGlideModule annotationGeneratedModule = getAnnotationGeneratedGlideModules();
        List<GlideModule> manifestModules = Collections.emptyList();
        if (annotationGeneratedModule == null || annotationGeneratedModule.isManifestParsingEnabled()) {
            manifestModules = new ManifestParser(applicationContext).parse();
        }

        if (annotationGeneratedModule != null
                && !annotationGeneratedModule.getExcludedModuleClasses().isEmpty()) {
            Set<Class<?>> excludedModuleClasses =
                    annotationGeneratedModule.getExcludedModuleClasses();
            for (Iterator<GlideModule> iterator = manifestModules.iterator(); iterator.hasNext(); ) {
                GlideModule current = iterator.next();
                if (!excludedModuleClasses.contains(current.getClass())) {
                    continue;
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "RootGlideModule excludes manifest GlideModule: " + current);
                }
                iterator.remove();
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            for (GlideModule glideModule : manifestModules) {
                Log.d(TAG, "Discovered GlideModule from manifest: " + glideModule.getClass());
            }
        }

        RequestManagerRetriever.RequestManagerFactory factory =
                annotationGeneratedModule != null
                        ? annotationGeneratedModule.getRequestManagerFactory() : null;
        GlideBuilder builder = new GlideBuilder()
                .setRequestManagerFactory(factory);
        for (GlideModule module : manifestModules) {
            module.applyOptions(applicationContext, builder);
        }
        if (annotationGeneratedModule != null) {
            annotationGeneratedModule.applyOptions(applicationContext, builder);
        }
        glide = builder.createGlide(applicationContext);
        for (GlideModule module : manifestModules) {
            module.registerComponents(applicationContext, glide.registry);
        }
        if (annotationGeneratedModule != null) {
            annotationGeneratedModule.registerComponents(applicationContext, glide.registry);
        }
    }

    @Nullable
    @SuppressWarnings({"unchecked", "deprecation"})
    private static GeneratedRootGlideModule getAnnotationGeneratedGlideModules() {
        GeneratedRootGlideModule result = null;
        try {
            Class<GeneratedRootGlideModule> clazz =
                    (Class<GeneratedRootGlideModule>)
                            Class.forName("com.bumptech.glide.GeneratedRootGlideModuleImpl");
            result = clazz.newInstance();
        } catch (ClassNotFoundException e) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Failed to find GeneratedRootGlideModule. You should include an"
                        + " annotationProcessor compile dependency on com.github.bumptech.glide:glide:compiler"
                        + " in your application and a @GlideModule annotated RootGlideModule implementation or"
                        + " ChildGlideModules will be silently ignored");
            }
        } catch (InstantiationException e) {
            throw new IllegalStateException("GeneratedRootGlideModuleImpl is implemented incorrectly."
                    + " If you've manually implemented this class, remove your implementation. The Annotation"
                    + " processor will generate a correct implementation.", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("GeneratedRootGlideModuleImpl is implemented incorrectly."
                    + " If you've manually implemented this class, remove your implementation. The Annotation"
                    + " processor will generate a correct implementation.", e);
        }
        return result;
    }

    /**
     * 将单例Glide实例置空
     */
    @VisibleForTesting
    public static void tearDown() {
        glide = null;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    Glide(Context context,
          Engine engine,
          MemoryCache memoryCache,
          BitmapPool bitmapPool,
          ArrayPool arrayPool,
          RequestManagerRetriever requestManagerRetriever,
          ConnectivityMonitorFactory connectivityMonitorFactory,
          int logLevel,
          RequestOptions defaultRequestOptions) {
        this.engine = engine;
        this.bitmapPool = bitmapPool;
        this.arrayPool = arrayPool;
        this.memoryCache = memoryCache;
        this.requestManagerRetriever = requestManagerRetriever;
        this.connectivityMonitorFactory = connectivityMonitorFactory;

        DecodeFormat decodeFormat = defaultRequestOptions.getOptions().get(Downsampler.DECODE_FORMAT);
        bitmapPreFiller = new BitmapPreFiller(memoryCache, bitmapPool, decodeFormat);

        final Resources resources = context.getResources();

        registry = new Registry();
        registry.register(new DefaultImageHeaderParser());

        Downsampler downsampler = new Downsampler(registry.getImageHeaderParsers(),
                resources.getDisplayMetrics(), bitmapPool, arrayPool);
        ByteBufferGifDecoder byteBufferGifDecoder =
                new ByteBufferGifDecoder(context, registry.getImageHeaderParsers(), bitmapPool, arrayPool);
        registry.register(ByteBuffer.class, new ByteBufferEncoder())
                .register(InputStream.class, new StreamEncoder(arrayPool))
                .append(ByteBuffer.class, Bitmap.class, new ByteBufferBitmapDecoder(downsampler))
                .append(InputStream.class, Bitmap.class, new StreamBitmapDecoder(downsampler, arrayPool))
                .append(ParcelFileDescriptor.class, Bitmap.class, new VideoBitmapDecoder(bitmapPool))
                .register(Bitmap.class, new BitmapEncoder())
                .append(ByteBuffer.class, BitmapDrawable.class, new BitmapDrawableDecoder<>(resources, bitmapPool, new ByteBufferBitmapDecoder(downsampler)))
                .append(InputStream.class, BitmapDrawable.class, new BitmapDrawableDecoder<>(resources, bitmapPool, new StreamBitmapDecoder(downsampler, arrayPool)))
                .append(ParcelFileDescriptor.class, BitmapDrawable.class, new BitmapDrawableDecoder<>(resources, bitmapPool, new VideoBitmapDecoder(bitmapPool)))
                .register(BitmapDrawable.class, new BitmapDrawableEncoder(bitmapPool, new BitmapEncoder()))
                .prepend(InputStream.class, GifDrawable.class, new StreamGifDecoder(registry.getImageHeaderParsers(), byteBufferGifDecoder, arrayPool))
                .prepend(ByteBuffer.class, GifDrawable.class, byteBufferGifDecoder)
                .register(GifDrawable.class, new GifDrawableEncoder())
                .append(GifDecoder.class, GifDecoder.class, new UnitModelLoader.Factory<GifDecoder>())
                .append(GifDecoder.class, Bitmap.class, new GifFrameResourceDecoder(bitmapPool))
                .register(new ByteBufferRewinder.Factory())
                .append(File.class, ByteBuffer.class, new ByteBufferFileLoader.Factory())
                .append(File.class, InputStream.class, new FileLoader.StreamFactory())
                .append(File.class, File.class, new FileDecoder())
                .append(File.class, ParcelFileDescriptor.class, new FileLoader.FileDescriptorFactory())
                .append(File.class, File.class, new UnitModelLoader.Factory<File>())
                .register(new InputStreamRewinder.Factory(arrayPool))
                .append(int.class, InputStream.class, new ResourceLoader.StreamFactory(resources))
                .append(int.class, ParcelFileDescriptor.class, new ResourceLoader.FileDescriptorFactory(resources))
                .append(Integer.class, InputStream.class, new ResourceLoader.StreamFactory(resources))
                .append(Integer.class, ParcelFileDescriptor.class, new ResourceLoader.FileDescriptorFactory(resources))
                .append(String.class, InputStream.class, new DataUrlLoader.StreamFactory())
                .append(String.class, InputStream.class, new StringLoader.StreamFactory())
                .append(String.class, ParcelFileDescriptor.class, new StringLoader.FileDescriptorFactory())
                .append(Uri.class, InputStream.class, new HttpUriLoader.Factory())
                .append(Uri.class, InputStream.class, new AssetUriLoader.StreamFactory(context.getAssets()))
                .append(Uri.class, ParcelFileDescriptor.class, new AssetUriLoader.FileDescriptorFactory(context.getAssets()))
                .append(Uri.class, InputStream.class, new MediaStoreImageThumbLoader.Factory(context))
                .append(Uri.class, InputStream.class, new MediaStoreVideoThumbLoader.Factory(context))
                .append(Uri.class, InputStream.class, new UriLoader.StreamFactory(context.getContentResolver()))
                .append(Uri.class, ParcelFileDescriptor.class, new UriLoader.FileDescriptorFactory(context.getContentResolver()))
                .append(Uri.class, InputStream.class, new UrlUriLoader.StreamFactory())
                .append(URL.class, InputStream.class, new UrlLoader.StreamFactory())
                .append(Uri.class, File.class, new MediaStoreFileLoader.Factory(context))
                .append(GlideUrl.class, InputStream.class, new HttpGlideUrlLoader.Factory())
                .append(byte[].class, ByteBuffer.class, new ByteArrayLoader.ByteBufferFactory())
                .append(byte[].class, InputStream.class, new ByteArrayLoader.StreamFactory())
                .register(Bitmap.class, BitmapDrawable.class, new BitmapDrawableTranscoder(resources, bitmapPool))
                .register(Bitmap.class, byte[].class, new BitmapBytesTranscoder())
                .register(GifDrawable.class, byte[].class, new GifDrawableBytesTranscoder());

        ImageViewTargetFactory imageViewTargetFactory = new ImageViewTargetFactory();
        glideContext = new GlideContext(context, registry, imageViewTargetFactory, defaultRequestOptions, engine, this, logLevel);
    }

    /**
     * Returns the {@link BitmapPool} used to
     * temporarily store {@link Bitmap}s so they can be reused to avoid garbage
     * collections.
     * <p>
     * 返回避免被垃圾回收而用来临时存储Bitmap图片的BitmapPool
     * <p>
     * Note - Using this pool directly can lead to undefined behavior and strange drawing errors.
     * Any {@link Bitmap} added to the pool must not be currently in use in any other
     * part of the application. Any {@link Bitmap} added to the pool must be removed
     * from the pool before it is added a second time.
     * <p>
     * 直接使用这个pool可能导致未定义行为和图片错误绘制，
     * 任何被添加到该pool中的Bitmap一定不能直接使用在Application中，
     * 任何Bitmap在第二次被添加到该pool前必须将其从该pool中移除掉
     * <p>
     * Note - To make effective use of the pool, any {@link Bitmap} removed from
     * the pool must eventually be re-added. Otherwise the pool will eventually empty and will not
     * serve any useful purpose.
     * <p>
     * 任何Bitmap从该pool中移除后必须重新添加，否则不能提供正常的服务
     * <p>
     * The primary reason this object is exposed is for use in custom
     * {@link com.bumptech.glide.load.ResourceDecoder}s and
     * {@link com.bumptech.glide.load.Transformation}s. Use outside of these classes is not generally
     * recommended.
     * <p>
     * 之所以公开该接口是为了自定义{@link com.bumptech.glide.load.ResourceDecoder}和
     * {@link com.bumptech.glide.load.Transformation}但并不推荐随意调用这些类
     */
    public BitmapPool getBitmapPool() {
        return bitmapPool;
    }

    public ArrayPool getArrayPool() {
        return arrayPool;
    }

    /**
     * @return The context associated with this instance.
     * <p>
     * 返回这个单例Glide实例相关联的Context
     */
    public Context getContext() {
        return glideContext.getBaseContext();
    }

    ConnectivityMonitorFactory getConnectivityMonitorFactory() {
        return connectivityMonitorFactory;
    }

    GlideContext getGlideContext() {
        return glideContext;
    }

    /**
     * Pre-fills the {@link BitmapPool} using the given sizes.
     * <p>
     * 使用给定大小对BitmapPool进行预填充
     * <p>
     * Enough Bitmaps are added to completely fill the pool, so most or all of the Bitmaps
     * currently in the pool will be evicted. Bitmaps are allocated according to the weights of the
     * given sizes, where each size gets (weight / prefillWeightSum) percent of the pool to fill.
     * <p>
     * Note - Pre-filling is done asynchronously using and
     * {@link android.os.MessageQueue.IdleHandler}. Any currently running pre-fill will be cancelled
     * and replaced by a call to this method.
     * <p>
     * This method should be used with caution, overly aggressive pre-filling is substantially
     * worse than not pre-filling at all. Pre-filling should only be started in onCreate to avoid
     * constantly clearing and re-filling the {@link BitmapPool}. Rotation should be carefully
     * considered as well. It may be worth calling this method only when no saved instance state
     * exists so that pre-filling only happens when the Activity is first created, rather than on
     * every rotation.
     * <p>
     * 该方法需要谨慎调用，过于激进地运行不如不运行；预填充仅需在{@code onCreate}进行，以免不断地
     * 清理和再填充{@link BitmapPool}
     *
     * @param bitmapAttributeBuilders The list of
     *                                {@link PreFillType.Builder Builders} representing
     *                                individual sizes and configurations of {@link Bitmap}s to be pre-filled.
     */
    public void preFillBitmapPool(PreFillType.Builder... bitmapAttributeBuilders) {
        bitmapPreFiller.preFill(bitmapAttributeBuilders);
    }

    /**
     * Clears as much memory as possible.
     * <p>
     * 尽可能多地清理内存
     *
     * @see android.content.ComponentCallbacks#onLowMemory()
     * @see ComponentCallbacks2#onLowMemory()
     */
    public void clearMemory() {
        // Engine asserts this anyway when removing resources, fail faster and consistently
        Util.assertMainThread();
        // memory cache needs to be cleared before bitmap pool to clear re-pooled Bitmaps too. See #687.
        memoryCache.clearMemory();
        bitmapPool.clearMemory();
        arrayPool.clearMemory();
    }

    /**
     * Clears some memory with the exact amount depending on the given level.
     * <p>
     * 根据给定的level来清理特定数量的内存
     *
     * @see ComponentCallbacks2#onTrimMemory(int)
     */
    public void trimMemory(int level) {
        // Engine asserts this anyway when removing resources, fail faster and consistently
        Util.assertMainThread();
        // memory cache needs to be trimmed before bitmap pool to trim re-pooled Bitmaps too. See #687.
        memoryCache.trimMemory(level);
        bitmapPool.trimMemory(level);
        arrayPool.trimMemory(level);
    }

    /**
     * Clears disk cache.
     * <p>
     * 清理磁盘缓存
     * <p>
     * This method should always be called on a background thread, since it is a blocking call.
     * <p>
     * 该方法需在后台线程调用
     */
    public void clearDiskCache() {
        Util.assertBackgroundThread();
        engine.clearDiskCache();
    }

    /**
     * Internal method.
     */
    public RequestManagerRetriever getRequestManagerRetriever() {
        return requestManagerRetriever;
    }

    /**
     * Adjusts Glide's current and maximum memory usage based on the given {@link MemoryCategory}.
     * <p>
     * 基于给定的{@link MemoryCategory}来调整Glide当前和最大能使用的内存
     * <p>
     * The default {@link MemoryCategory} is {@link MemoryCategory#NORMAL}.
     * {@link MemoryCategory#HIGH} increases Glide's maximum memory usage by up to 50% and
     * {@link MemoryCategory#LOW} decreases Glide's maximum memory usage by 50%. This method should be
     * used to temporarily increase or decrease memory usage for a single Activity or part of the app.
     * Use {@link GlideBuilder#setMemoryCache(MemoryCache)} to put a permanent memory size if you want
     * to change the default.
     * <p>
     * 这个方法只是用来临时增加或减少某个Activity或应用某部分的内存，要想设置的内存值是恒定的，需要使用
     * {@link GlideBuilder#setMemoryCache(MemoryCache)}
     *
     * @return the previous MemoryCategory used by Glide.
     * <p>
     * 返回Glide之前的MemoryCategory
     */
    public MemoryCategory setMemoryCategory(MemoryCategory memoryCategory) {
        // Engine asserts this anyway when removing resources, fail faster and consistently
        Util.assertMainThread();
        // memory cache needs to be trimmed before bitmap pool to trim re-pooled Bitmaps too. See #687.
        memoryCache.setSizeMultiplier(memoryCategory.getMultiplier());
        bitmapPool.setSizeMultiplier(memoryCategory.getMultiplier());
        MemoryCategory oldCategory = this.memoryCategory;
        this.memoryCategory = memoryCategory;
        return oldCategory;
    }

    private static RequestManagerRetriever getRetriever(@Nullable Context context) {
        // Context could be null for other reasons (ie the user passes in null), but in practice it will
        // only occur due to errors with the Fragment lifecycle.
        if (context == null) {
            throw new IllegalArgumentException(
                    "You cannot start a load on a Fragment where getActivity() returns null (which usually"
                            + " occurs when getActivity() is called before the Fragment is attached or after the"
                            + " Fragment is destroyed).");
        }
        return Glide.get(context).getRequestManagerRetriever();
    }

    /**
     * Begin a load with Glide by passing in a context.
     * <p>
     * 通过传入一个Context来用Glide进行加载
     * <p>
     * Any requests started using a context will only have the application level options applied
     * and will not be started or stopped based on lifecycle events. In general, loads should be
     * started at the level the result will be used in. If the resource will be used in a view in a
     * child fragment, the load should be started with {@link #with(android.app.Fragment)}} using that
     * child fragment. Similarly, if the resource will be used in a view in the parent fragment, the
     * load should be started with {@link #with(android.app.Fragment)} using the parent fragment. In
     * the same vein, if the resource will be used in a view in an activity, the load should be
     * started with {@link #with(Activity)}.
     * <p>
     * 传入一个Context来请求加载仅适用于应用的部分，因为它不会基于生命周期事件来进行控制
     * 若加载的控件在Fragment内，则应该使用{@link #with(android.app.Fragment)}
     * 同样的，在Activity内加载的控件应该选择{@link #with(Activity)}
     * <p>
     * This method is appropriate for resources that will be used outside of the normal fragment
     * or activity lifecycle (For example in services, or for notification thumbnails).
     * <p>
     * 该方法适用于在Activity和Fragment生命周期之外使用，如在Service或Notification中使用
     *
     * @param context Any context, will not be retained.
     * @return A RequestManager for the top level application that can be used to start a load.
     * @see #with(Activity)
     * @see #with(android.app.Fragment)
     * @see #with(Fragment)
     * @see #with(FragmentActivity)
     * @see RequestManagerRetriever#get(Context)
     */
    public static RequestManager with(Context context) {
        return getRetriever(context).get(context);
    }

    /**
     * Begin a load with Glide that will be tied to the given {@link Activity}'s lifecycle
     * and that uses the given {@link Activity}'s default options.
     * <p>
     * 用Glide加载会和给定的Activity的生命周期进行关联
     *
     * @param activity The activity to use.
     * @return A RequestManager for the given activity that can be used to start a load.
     * @see RequestManagerRetriever#get(Activity)
     */
    public static RequestManager with(Activity activity) {
        return getRetriever(activity).get(activity);
    }

    /**
     * Begin a load with Glide that will tied to the give
     * {@link FragmentActivity}'s lifecycle and that uses the given
     * {@link FragmentActivity}'s default options.
     *
     * @param activity The activity to use.
     * @return A RequestManager for the given FragmentActivity that can be used to start a load.
     * @see RequestManagerRetriever#get(FragmentActivity)
     */
    public static RequestManager with(FragmentActivity activity) {
        return getRetriever(activity).get(activity);
    }

    /**
     * Begin a load with Glide that will be tied to the given {@link android.app.Fragment}'s lifecycle
     * and that uses the given {@link android.app.Fragment}'s default options.
     *
     * @param fragment The fragment to use.
     * @return A RequestManager for the given Fragment that can be used to start a load.
     * @see RequestManagerRetriever#get(android.app.Fragment)
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static RequestManager with(android.app.Fragment fragment) {
        return getRetriever(fragment.getActivity()).get(fragment);
    }

    /**
     * Begin a load with Glide that will be tied to the given
     * {@link Fragment}'s lifecycle and that uses the given
     * {@link Fragment}'s default options.
     *
     * @param fragment The fragment to use.
     * @return A RequestManager for the given Fragment that can be used to start a load.
     * @see RequestManagerRetriever#get(Fragment)
     */
    public static RequestManager with(Fragment fragment) {
        return getRetriever(fragment.getActivity()).get(fragment);
    }

    public Registry getRegistry() {
        return registry;
    }

    void removeFromManagers(Target<?> target) {
        synchronized (managers) {
            for (RequestManager requestManager : managers) {
                if (requestManager.untrack(target)) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Failed to remove target from managers");
    }

    void registerRequestManager(RequestManager requestManager) {
        synchronized (managers) {
            if (managers.contains(requestManager)) {
                throw new IllegalStateException("Cannot register already registered manager");
            }
            managers.add(requestManager);
        }
    }

    void unregisterRequestManager(RequestManager requestManager) {
        synchronized (managers) {
            if (!managers.contains(requestManager)) {
                throw new IllegalStateException("Cannot register not yet registered manager");
            }
            managers.remove(requestManager);
        }
    }

    /**
     * 该回调是从API{@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH}开始被引入的
     *
     * @param level 参数为{@link ComponentCallbacks2#TRIM_MEMORY_UI_HIDDEN}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_RUNNING_MODERATE}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_RUNNING_LOW}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_RUNNING_CRITICAL}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_BACKGROUND}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_MODERATE}、
     *              {@link ComponentCallbacks2#TRIM_MEMORY_COMPLETE}当中的一个
     */
    @Override
    public void onTrimMemory(int level) {
        trimMemory(level);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Do nothing.
    }

    @Override
    public void onLowMemory() {
        clearMemory();
    }
}
