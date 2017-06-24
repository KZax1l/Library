package com.bumptech.glide.module;

import android.content.Context;

import com.bumptech.glide.GlideBuilder;

/**
 * Defines a set of dependencies and options to use when initializing Glide within an application.
 * <p>
 * <p>There can be at most one {@link AppGlideModule} in an application. Only Applications
 * can include a {@link AppGlideModule}. Libraries must use {@link ChildGlideModule}.
 * <p>
 * <p>Classes that extend {@link AppGlideModule} must be annotated with
 * {@link com.bumptech.glide.annotation.GlideModule} to be processed correctly.
 * <p>
 * <p>Classes that extend {@link AppGlideModule} can optionally be annotated with
 * {@link com.bumptech.glide.annotation.Excludes} to optionally exclude one or more
 * {@link ChildGlideModule} and/or {@link GlideModule} classes.
 * <p>
 * <p>Once an application has migrated itself and all libraries it depends on to use Glide's
 * annotation processor, {@link AppGlideModule} implementations should override
 * {@link #isManifestParsingEnabled()} and return {@code false}.
 */
public abstract class AppGlideModule extends ChildGlideModule implements AppliesOptions {
    /**
     * Returns {@code true} if Glide should check the AndroidManifest for {@link GlideModule}s.
     * <p>
     * <p>Implementations should return {@code false} after they and their dependencies have migrated
     * to Glide's annotation processor.
     * <p>
     * <p>Returns {@code true} by default.
     */
    public boolean isManifestParsingEnabled() {
        return true;
    }

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // Default empty impl.
    }
}