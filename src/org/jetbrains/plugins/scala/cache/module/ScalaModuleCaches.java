package org.jetbrains.plugins.scala.cache.module;

import org.jetbrains.plugins.scala.cache.ScalaFilesCache;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Ilya.Sergey
 */
public interface ScalaModuleCaches extends ScalaFilesCache {

  public void processFileChanged(final @NotNull VirtualFile file);

  public void processFileDeleted(final @NotNull String fileUrl);

  public void refresh();

}
