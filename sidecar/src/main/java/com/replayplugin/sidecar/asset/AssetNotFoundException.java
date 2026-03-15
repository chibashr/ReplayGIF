package com.replayplugin.sidecar.asset;

/**
 * Thrown when an asset cannot be loaded from cache and fallback (e.g. mcasset.cloud) is unavailable or fails.
 */
public class AssetNotFoundException extends RuntimeException {

    private final String assetIdentifier;

    public AssetNotFoundException(String assetIdentifier) {
        super("Asset not found: " + assetIdentifier);
        this.assetIdentifier = assetIdentifier;
    }

    public AssetNotFoundException(String assetIdentifier, Throwable cause) {
        super("Asset not found: " + assetIdentifier, cause);
        this.assetIdentifier = assetIdentifier;
    }

    public String getAssetIdentifier() {
        return assetIdentifier;
    }
}
