package org.jetbrains.plugins.scala.packagesearch.api;

import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion;

// See https://discuss.kotlinlang.org/t/scala-does-not-see-kotlin-companion-object-functions/21880/6
public final class NormalizedVersionCompanionProxy {
    private NormalizedVersionCompanionProxy() {
    }

    public static NormalizedVersion from(String version) {
        return NormalizedVersion.Companion.from(version, null);
    }
}
