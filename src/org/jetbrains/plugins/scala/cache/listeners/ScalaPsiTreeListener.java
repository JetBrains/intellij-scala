/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
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

import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.io.fs.FileSystem;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCaches;

/**
 * @author Ilya.Sergey
 */
public class ScalaPsiTreeListener implements PsiTreeChangeListener {

  ScalaModuleCaches myScalaModuleCaches;

  public ScalaPsiTreeListener(ScalaModuleCaches scalaModuleCaches) {
    myScalaModuleCaches = scalaModuleCaches;
  }

  private void updateCaches(PsiTreeChangeEvent event) {
    if (event.getFile() != null) {
      myScalaModuleCaches.processFileChanged(event.getFile().getVirtualFile());
      myScalaModuleCaches.refresh();
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
    if (event.getChild() instanceof PsiFile) {
      String parentUrl = ((PsiDirectory)event.getParent()).getVirtualFile().getUrl();
      String url = parentUrl + '/'
              + ((PsiFile) event.getChild()).getVirtualFile().getName();
      myScalaModuleCaches.processFileDeleted(url);
      myScalaModuleCaches.refresh();
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
