package org.jetbrains.plugins.scala.packagesearch.utils;

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope;

// See https://discuss.kotlinlang.org/t/scala-does-not-see-kotlin-companion-object-functions/21880/6
public class PackageScopeCompanionProxy {
    public final static PackageScope.Companion companion = PackageScope.Companion;
}
