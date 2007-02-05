package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Storage for file information
 *
 * @author Ilya.Sergey
 */
public class ScalaFilesStorageImpl implements ScalaFilesStorage {

  private final Map<String, ScalaFileInfo> myUrl2FileInfo =
          Collections.synchronizedMap(new HashMap<String, ScalaFileInfo>());

  public ScalaFileInfo getScalaFileInfoByFileUrl(@NotNull final String fileUrl) {
    return myUrl2FileInfo.get(fileUrl);
  }

  public void addScalaInfo(@NotNull final ScalaFileInfo sInfo) {
    myUrl2FileInfo.put(sInfo.getFileUrl() + "." + sInfo.getFileName(), sInfo);
  }

  public Collection<ScalaFileInfo> getAllScalaFileInfos() {
    return Collections.unmodifiableCollection(myUrl2FileInfo.values());
  }

  public ScalaFileInfo removeScalaInfo(@NotNull final String fileUrl) {
    return myUrl2FileInfo.remove(fileUrl);
  }
}
