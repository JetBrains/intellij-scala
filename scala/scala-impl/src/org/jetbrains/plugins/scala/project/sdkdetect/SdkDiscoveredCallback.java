package org.jetbrains.plugins.scala.project.sdkdetect;

import org.jetbrains.plugins.scala.project.template.SdkChoice;

public interface SdkDiscoveredCallback {
    void sdkDiscovered(SdkChoice sdk);
}
