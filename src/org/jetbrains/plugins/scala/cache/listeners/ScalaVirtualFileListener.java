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

package org.jetbrains.plugins.scala.cache.listeners;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCachesManager;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCaches;
import org.jetbrains.plugins.scala.util.ScalaUtils;

/**
 * Implements all activity, related to virual file system changes
 *
 * @author Ilya.Sergey
 */
public class ScalaVirtualFileListener extends VirtualFileAdapter {

  private ScalaModuleCachesManager myManager;

  public ScalaVirtualFileListener(ScalaModuleCachesManager manager) {
    myManager = manager;
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.DELETED);
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.DELETED);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.CHANGED);
  }

  public void contentsChanged(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.CHANGED);
  }

  public void fileCreated(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.CHANGED);
  }

  public void fileMoved(VirtualFileMoveEvent event) {
    processFile(event.getFile(), event.getParent(), FILE_CHANGE_TYPES.CHANGED);
  }

  private void processFile(VirtualFile file, VirtualFile parent, FILE_CHANGE_TYPES type) {
    if (parent == null || file == null) {
      return;
    }
    if (!file.isDirectory()) {
      processFile(file, type);
      return;
    }
    if (ScalaUtils.isVersionControlSysDir(file)) {
      return;
    }
    VirtualFile[] files = file.getChildren();
    for (VirtualFile virtualFile : files) {
      processFile(virtualFile, file, type);
    }
  }

  private void processFile(VirtualFile file, FILE_CHANGE_TYPES type) {
    switch (type) {
      case CHANGED: {
        myManager.getModuleFilesCache().processFileChanged(file);
        break;
      }
      case DELETED: {
        myManager.getModuleFilesCache().processFileDeleted(file.getUrl());
        break;
      }
    }
  }


}

/**
 * Shows current state of processed file
 */
enum FILE_CHANGE_TYPES {
  DELETED,
  CHANGED
}



