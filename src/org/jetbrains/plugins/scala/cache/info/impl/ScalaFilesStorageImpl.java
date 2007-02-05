package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import com.intellij.util.io.fs.FileSystem;
import com.intellij.psi.PsiClass;

/**
 * Storage for file information
 *
 * @author Ilya.Sergey
 */
public class ScalaFilesStorageImpl implements ScalaFilesStorage {

  private final Map<String, ScalaFileInfo> myUrl2FileInfo =
          Collections.synchronizedMap(new HashMap<String, ScalaFileInfo>());

  private final Map<String, ScalaFileInfo> myClass2FileInfo =
          Collections.synchronizedMap(new HashMap<String, ScalaFileInfo>());

  public ScalaFileInfo getScalaFileInfoByFileUrl(@NotNull final String fileUrl) {
    return myUrl2FileInfo.get(fileUrl);
  }

  public void addScalaInfo(@NotNull final ScalaFileInfo sInfo) {
    myUrl2FileInfo.put(sInfo.getFileUrl(), sInfo);
    for (String clazz : sInfo.getClassNames()) {
      myClass2FileInfo.put(clazz, sInfo);
    }
  }

  public ScalaFileInfo removeScalaInfo(@NotNull final String fileUrl) {
    ScalaFileInfo info = myUrl2FileInfo.remove(fileUrl);
    if (info != null) {
      for (String clazz : info.getClassNames()) {
        myClass2FileInfo.remove(clazz);
      }
    }
    return info;
  }

  public PsiClass getClassByName(@NotNull final String name) {
    ScalaFileInfo info = getFileInfoByClassName(name);
    if (info != null) {
      return info.getClassByName(name);
    }
    return null;
  }

  public PsiClass[] getClassesByName(@NotNull final String name) {
    ScalaFileInfo info = getFileInfoByClassName(name);
    if (info != null) {
      return info.getClassesByName(name);
    }
    return null;
  }

  private ScalaFileInfo getFileInfoByClassName(String name) {
    return myClass2FileInfo.get(name);
  }

  public String getFileUrlByClassName(@NotNull final String name) {
    if (myClass2FileInfo.get(name) != null) {
      return myClass2FileInfo.get(name).getFileUrl();
    }
    return null;
  }

  public Collection<ScalaFileInfo> getAllScalaFileInfos() {
    return Collections.unmodifiableCollection(myUrl2FileInfo.values());
  }
}
