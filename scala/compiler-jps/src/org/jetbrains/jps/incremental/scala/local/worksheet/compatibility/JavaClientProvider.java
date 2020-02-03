package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

public interface JavaClientProvider {
  void onProgress(String message);
  void onInitializationException(Exception ex);
}
