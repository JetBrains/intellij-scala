package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Sergey
 */
public interface ScalaFilesCache {

  public void init (boolean  b);

  public void setCacheUrls(@NotNull String[] myCacheUrls);
  
}
