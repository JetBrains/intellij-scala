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
    if (ScalaUtils.isVersionControlSysDir(file)) {
      return;
    }
    if (!file.isDirectory()) {
      processFile(file, type);
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



