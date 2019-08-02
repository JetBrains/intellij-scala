package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

@SuppressWarnings("Convert2Lambda")
public interface JavaClientProvider {
  void onProgress(String message);

  JavaClientProvider NO_OP_PROVIDER = new JavaClientProvider() {
    public void onProgress(String message) { }
  };
}
