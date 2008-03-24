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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;

/**
 * @author ilyas
 */
public class ScalaPsiTreeListener implements PsiTreeChangeListener {

  ScalaFilesCache myScalaCache;

  public ScalaPsiTreeListener(ScalaFilesCache cache) {
    myScalaCache = cache;
  }

  private void updateCaches(PsiTreeChangeEvent event) {
    if (event.getFile() != null || event.getOldChild() instanceof PsiFileSystemItem) {
      if (event.getOldChild() instanceof PsiFileSystemItem) {
        String parentUrl = ((PsiDirectory) event.getParent()).getVirtualFile().getUrl();
        String url = parentUrl + '/'
                + ((PsiFileSystemItem) event.getOldChild()).getVirtualFile().getName();
        VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
        if (vFile != null) {
          if (vFile.getParent() != null) {
            myScalaCache.processFileChanged(vFile.getParent());
          }
          myScalaCache.processFileChanged(vFile);
        }
      } else {
        myScalaCache.processFileChanged(event.getFile().getVirtualFile());
      }
      myScalaCache.refresh();
    }
  }

  public void beforeChildAddition(PsiTreeChangeEvent event) {
  }

  public void beforeChildRemoval(PsiTreeChangeEvent event) {
  }

  public void beforeChildReplacement(PsiTreeChangeEvent event) {
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
  }

  public void beforeChildrenChange(PsiTreeChangeEvent event) {
  }

  public void beforePropertyChange(PsiTreeChangeEvent event) {
  }

  public void childAdded(PsiTreeChangeEvent event) {
    updateCaches(event);
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    updateCaches(event);
    if (event.getOldChild() instanceof PsiFileSystemItem) {
      String parentUrl = ((PsiDirectory) event.getParent()).getVirtualFile().getUrl();
      String url = parentUrl + '/'
              + ((PsiFileSystemItem) event.getOldChild()).getVirtualFile().getName();
      VirtualFileManager manager = VirtualFileManager.getInstance();
      VirtualFile vParent = manager.findFileByUrl(parentUrl);
      if (vParent != null) {
        myScalaCache.processFileChanged(vParent);
      }
      myScalaCache.processFileDeleted(url);
    }
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    updateCaches(event);
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    updateCaches(event);
  }

  public void childMoved(PsiTreeChangeEvent event) {
    updateCaches(event);
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
  }
}