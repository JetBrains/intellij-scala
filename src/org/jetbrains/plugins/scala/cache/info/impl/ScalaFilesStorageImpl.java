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

  private final Map<String, ShortNameInfo> myShortClass2FileInfo =
          Collections.synchronizedMap(new HashMap<String, ShortNameInfo>());


  public ScalaFileInfo getScalaFileInfoByFileUrl(@NotNull final String fileUrl) {
    return myUrl2FileInfo.get(fileUrl);
  }

  public void addScalaInfo(@NotNull final ScalaFileInfo sInfo) {
    myUrl2FileInfo.put(sInfo.getFileUrl(), sInfo);
    for (String clazz : sInfo.getClassNames()) {
      myClass2FileInfo.put(clazz, sInfo);

      ShortNameInfo shortInfo = myShortClass2FileInfo.get(getShortName(clazz));
      if (shortInfo != null) {
        Set<String> urls = shortInfo.getFileUrls();
        urls.add(sInfo.getFileUrl());
        myShortClass2FileInfo.put(getShortName(clazz),
                new ShortNameInfo(getShortName(clazz), urls)
        );
      } else {
        Set<String> urls = new HashSet<String>();
        urls.add(sInfo.getFileUrl());
        myShortClass2FileInfo.put(getShortName(clazz),
                new ShortNameInfo(getShortName(clazz), urls)
        );
      }

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

        ShortNameInfo shortInfo = myShortClass2FileInfo.get(getShortName(clazz));
        if (shortInfo != null) {
          Set<String> urls = shortInfo.getFileUrls();
          urls.remove(info.getFileUrl());
          if (urls.size() == 0) {
            myShortClass2FileInfo.remove(getShortName(clazz));
          } else
          myShortClass2FileInfo.put(getShortName(clazz),
                  new ShortNameInfo(getShortName(clazz), urls)
          );
        }
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

  public String[] getFileUrlsByShortClassName(@NotNull final String name) {
    if (myShortClass2FileInfo.get(name) != null) {
      return myShortClass2FileInfo.get(name).getFileUrls().toArray(new String[0]);
    }
    return new String[0];
  }


  public Collection<String> getAllClassNames() {
    return myClass2FileInfo.keySet();
  }

  public Collection<String> getAllClassShortNames() {
    return myShortClass2FileInfo.keySet();
  }

  protected static String getShortName(String qualName) {
    int index = qualName.lastIndexOf('.');
    if (index < 0 || index >= qualName.length() - 1) {
      return qualName;
    } else {
      return qualName.substring(index + 1);
    }
  }

}
