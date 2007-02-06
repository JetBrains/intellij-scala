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
