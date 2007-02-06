package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

  public Collection<ScalaFileInfo> getAllScalaFileInfos() {
    return Collections.unmodifiableCollection(myUrl2FileInfo.values());
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

  public String getFileUrlByClassName(@NotNull final String name) {
    if (myClass2FileInfo.get(name) != null) {
      return myClass2FileInfo.get(name).getFileUrl();
    }
    return null;
  }

  public Collection<String> getAllClassNames() {
      return myClass2FileInfo.keySet();
  }

  public Collection<String> getAllClassShortNames() {
    Collection<String> qualNames = myClass2FileInfo.keySet();
    ArrayList<String> acc = new ArrayList<String>();
    for (String qualName: qualNames){
      int index = qualName.lastIndexOf('.');
      if (index < 0 || index >= qualName.length()-1) {
        acc.add(qualName);
      } else {
        acc.add(qualName.substring(index+1));
      }
    }
    return acc;
  }
}
