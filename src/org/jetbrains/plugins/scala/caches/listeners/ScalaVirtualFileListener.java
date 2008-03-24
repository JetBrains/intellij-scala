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

package org.jetbrains.plugins.scala.caches.listeners;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.*;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;
import org.jetbrains.plugins.scala.caches.project.ScalaCachesManagerImpl;
import org.jetbrains.plugins.scala.util.ScalaUtils;

/**
 * @author ilyas
 */
public class ScalaVirtualFileListener extends VirtualFileAdapter {

  private ScalaCachesManagerImpl myManager;

  public ScalaVirtualFileListener(ScalaCachesManagerImpl manager) {
    myManager = manager;
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.DELETED);
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.DELETED);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.CHANGED);
  }

  public void contentsChanged(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.CHANGED);
  }

  public void fileCreated(VirtualFileEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.CHANGED);
  }

  public void fileMoved(VirtualFileMoveEvent event) {
    processFile(event.getFile(), event.getParent(), ChangeType.CHANGED);
  }

  private void processFile(VirtualFile file, VirtualFile parent, ChangeType type) {
    if (parent == null || file == null) {
      return;
    }
    if (!ScalaUtils.isScalaFileOrDirectory(file)) {
      return;
    }
    processFile(file, type);
  }

  private void processFile(VirtualFile file, ChangeType type) {
    Module module = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getModuleForFile(file);
    if (module != null) {
      ScalaFilesCache cache = myManager.getModuleFilesCache(module);
      if (cache != null) {
        switch (type) {
          case CHANGED: {
            cache.processFileChanged(file);
            break;
          }
          case DELETED: {
            cache.processFileDeleted(file.getUrl());
            break;
          }
        }
      }
    }
  }
}

/**
 * Shows current state of processed file
 */
enum ChangeType {
  DELETED,
  CHANGED
}

