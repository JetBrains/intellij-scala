package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;

import java.util.Collection;

/**
 * @author Ilya.Sergey
 */
public interface ScalaFilesCache {

  public void init (boolean  b);

  public void setCacheUrls(@NotNull String[] myCacheUrls);

  public void setCacheName(@NotNull String myCacheName);

  public Collection<ScalaFileInfo> getAllClasses();

}
