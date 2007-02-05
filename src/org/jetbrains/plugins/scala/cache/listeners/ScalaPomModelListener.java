package org.jetbrains.plugins.scala.cache.listeners;

import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author Ilya.Sergey
 */
public abstract class ScalaPomModelListener {

  private Module myModule;
  private PomModel myPomModel;
  private ProjectFileIndex myFileIndex;

  public ScalaPomModelListener(final Module module, final PomModel pomModel) {
    myModule = module;
    myPomModel = pomModel;
    myFileIndex = ProjectRootManager.getInstance(myModule.getProject()).getFileIndex();
  }
}
