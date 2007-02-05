package org.jetbrains.plugins.scala.cache.info;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;

/**
 * Interface fo file storage
 *
 * @author Ilya.Sergey
 */
public interface ScalaFilesStorage extends Serializable {

  public ScalaFileInfo getScalaFileInfoByFileUrl(@NotNull final String fileUrl);

  public void addScalaInfo(@NotNull final ScalaFileInfo sInfo);

  public Collection<ScalaFileInfo> getAllScalaFileInfos();

  public ScalaFileInfo removeScalaInfo(@NotNull final String fileUrl);

}
