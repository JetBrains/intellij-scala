/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.caches.info.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.caches.info.ScalaDirectoryInfo;
import org.jetbrains.plugins.scala.caches.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.caches.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.caches.info.ScalaInfoBase;

import java.util.*;

/**
 * @author ilyas
 */
public class ScalaFilesStorageImpl implements ScalaFilesStorage {

  /**
   * Maps file urls to files
   */
  private final Map<String, ScalaInfoBase> myPath2FileInfo;

  /**
   * Maps class name to files
   */
  private final Map<String, NameInfo> myClass2FileInfo;

  /**
   * Maps short class name to files
   */
  private final Map<String, NameInfo> myShortClass2FileInfo;

  /**
   * Maps short name to occurrences in extends lists
   */
  private final Map<String, NameInfo> myClassName2ExtendsOccurrences;

  public ScalaFilesStorageImpl() {
    this(new HashMap<String, ScalaInfoBase>(),
        new HashMap<String, NameInfo>(),
        new HashMap<String, NameInfo>(),
        new HashMap<String, NameInfo>());
  }

  public ScalaFilesStorageImpl(Map<String, ScalaInfoBase> path2FileInfo, Map<String, NameInfo> class2FileInfo, Map<String, NameInfo> shortClass2FileInfo, Map<String, NameInfo> className2ExtendsOccurrences) {
    myPath2FileInfo = path2FileInfo;
    myClass2FileInfo = class2FileInfo;
    myShortClass2FileInfo = shortClass2FileInfo;
    myClassName2ExtendsOccurrences = className2ExtendsOccurrences;
  }

  public ScalaInfoBase getScalaFileInfoByFileUrl(@NotNull final String filePath) {
    return myPath2FileInfo.get(filePath);
  }

  public void addScalaInfo(@NotNull final ScalaInfoBase info) {
    final String fileUrl = info.getFileUrl();
    myPath2FileInfo.put(fileUrl, info);
    if (info instanceof ScalaFileInfo) {
      final ScalaFileInfo fileInfo = (ScalaFileInfo) info;
      for (String qname : fileInfo.getQualifiedClassNames()) {
        NameInfo qnameInfo = myClass2FileInfo.get(qname);
        if (qnameInfo == null) {
          qnameInfo = new NameInfo(qname);
          myClass2FileInfo.put(qname, qnameInfo);
        }
        qnameInfo.addFileUrl(fileUrl);

        final String shortName = getShortName(qname);
        NameInfo shortInfo = myShortClass2FileInfo.get(shortName);
        if (shortInfo == null) {
          shortInfo = new NameInfo(shortName);
          myShortClass2FileInfo.put(shortName, shortInfo);
        }
        shortInfo.addFileUrl(fileUrl);

        for (String extendedName : fileInfo.getExtendedNames()) {
          NameInfo nameInfo = myClassName2ExtendsOccurrences.get(extendedName);
          if (nameInfo == null) {
            nameInfo = new NameInfo(extendedName);
            myClassName2ExtendsOccurrences.put(extendedName, nameInfo);
          }
          nameInfo.addFileUrl(fileUrl);
        }
      }
    }
  }


  public ScalaInfoBase removeScalaInfo(@NotNull final String path, boolean recursively) {
    ScalaInfoBase info = myPath2FileInfo.get(path);
    if (info instanceof ScalaFileInfo) {
      String fileUrl = info.getFileUrl();
      for (String qname : ((ScalaFileInfo) info).getQualifiedClassNames()) {
        NameInfo qnameInfo = myClass2FileInfo.get(qname);
        if (qnameInfo != null) {
          qnameInfo.removeFileUrl(fileUrl);
          if (qnameInfo.isEmpty()) myClass2FileInfo.remove(qname);
        }
        String shortName = getShortName(qname);

        NameInfo shortInfo = myShortClass2FileInfo.get(shortName);
        if (shortInfo != null) {
          shortInfo.removeFileUrl(fileUrl);
          if (shortInfo.isEmpty()) myShortClass2FileInfo.remove(shortName);
        }
      }
    }

    if (recursively && info instanceof ScalaDirectoryInfo) {
      for (String childName : ((ScalaDirectoryInfo) info).getChildrenNames()) {
        String childPath = path + "/" + childName;
        removeScalaInfo(childPath, true);
      }
    }
    myPath2FileInfo.remove(path);
    return info;
  }

  public Set<String> getFileUrlsByClassName(@NotNull final String name) {
    final NameInfo info = myClass2FileInfo.get(name);
    if (info != null) return info.getFileUrls();
    return Collections.emptySet();
  }

  public Iterable<String> getFileUrlsByShortClassName(@NotNull final String name) {
    final NameInfo info = myShortClass2FileInfo.get(name);
    if (info != null) {
      return info.getFileUrls();
    }
    return Collections.emptyList();
  }

  @NotNull
  public Collection<String> getDerivedFileUrlsByShortClassName(@NotNull String name) {
    final NameInfo info = myClassName2ExtendsOccurrences.get(name);
    if (info != null) {
      return info.getFileUrls();
    }
    return Collections.emptyList();
  }

  public Collection<ScalaFileInfo> getAllScalaFileInfos() {
    List<ScalaFileInfo> result = new ArrayList<ScalaFileInfo>();
    for (ScalaInfoBase infoBase : myPath2FileInfo.values()) {
      if (infoBase instanceof ScalaFileInfo) {
        result.add((ScalaFileInfo) infoBase);
      }
    }
    return result;
  }

  public Collection<ScalaInfoBase> getAllInfos() {
    return Collections.unmodifiableCollection(myPath2FileInfo.values());
  }

  public Collection<String> getAllClassNames() {
    return myClass2FileInfo.keySet();
  }

  public Collection<String> getAllClassShortNames() {
    return myShortClass2FileInfo.keySet();
  }

  protected static String getShortName(String qualName) {
    int index = qualName.lastIndexOf('.');
    if (index < 0 || index >= (qualName.length() - 1)) {
      return qualName;
    } else {
      return qualName.substring(index + 1);
    }
  }

  public Map<String, ScalaInfoBase> getPath2FileInfo() {
    return myPath2FileInfo;
  }

  public Map<String, NameInfo> getClass2FileInfo() {
    return myClass2FileInfo;
  }

  public Map<String, NameInfo> getShortClass2FileInfo() {
    return myShortClass2FileInfo;
  }

  public Map<String, NameInfo> getClassName2ExtendsOccurrences() {
    return myClassName2ExtendsOccurrences;
  }
}
