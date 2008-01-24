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

package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.roots.ProjectFileIndex;

import java.util.Set;
import java.util.HashSet;

/**
 * Implements all auxiliary operations with virtual file system
 *
 * @author Ilya.Sergey
 */
public class VirtualFileScanner {

  /**
   * Scans recursively for scala source files
   *
   * @param virtualFile the root file
   * @param index
   * @return List of files
   */
  @NotNull
  public static Set<VirtualFile> scan(final VirtualFile virtualFile, ProjectFileIndex index) {
    final Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
    addScalaFiles(virtualFile, myFiles, index);
    return myFiles;
  }

  /**
   * Searches all scala files in directory.
   *
   * @param file     File or directory
   * @param allFiles Method adds all found files into this set
   * @param index    file index to check file ignored
   */
  public static void addScalaFiles(@Nullable final VirtualFile file, @NotNull final Set<VirtualFile> allFiles, ProjectFileIndex index) {
    if (file == null || ScalaUtils.isVersionControlSysDir(file) || index.isIgnored(file)) {
      return;
    }
    if (!file.isDirectory()) {
      if (ScalaUtils.isScalaFile(file)) {
        allFiles.add(file);
      }
      return;
    }
    final VirtualFile[] children = file.getChildren();
    for (final VirtualFile child : children) {
      addScalaFiles(child, allFiles, index);
    }
  }

  /**
   * Returns virtual file by URL
   *
   * @param url File URL
   * @return Instance of VirtualFile for given URL
   */
  public static VirtualFile getFileByUrl(String url) {
    if (url != null) {
      VirtualFileManager fManager = VirtualFileManager.getInstance();
      return fManager.findFileByUrl(url);
    }
    return null;
  }
}

