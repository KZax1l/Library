package com.bumptech.glide.request.target;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.transition.Transition;

/**
 * A one time use {@link Target} class that loads a resource into
 * memory and then clears itself.
 * <p>
 * 一个一次性使用的{@link Target}的实现类，用来加载资源到内存然后会清理掉它
 *
 * @param <Z> The type of resource that will be loaded into memory.
 */
public final class PreloadTarget<Z> extends SimpleTarget<Z> {

    private final RequestManager requestManager;

    /**
     * Returns a PreloadTarget.
     *
     * @param width  The width in pixels of the desired resource.
     * @param height The height in pixels of the desired resource.
     * @param <Z>    The type of the desired resource.
     */
    public static <Z> PreloadTarget<Z> obtain(RequestManager requestManager, int width, int height) {
        return new PreloadTarget<>(requestManager, width, height);
    }

    private PreloadTarget(RequestManager requestManager, int width, int height) {
        super(width, height);
        this.requestManager = requestManager;
    }

    @Override
    public void onResourceReady(Z resource, Transition<? super Z> transition) {
        requestManager.clear(this);
    }
}
