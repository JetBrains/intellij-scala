package org.jetbrains.plugins.scala.caches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class ScalaCachesManagerImpl implements ProjectComponent, ScalaCachesManager{

  private Project myProject;
  private ScalaShortNamesCacheImpl myCache;

  public static ScalaCachesManagerImpl getInstance(Project project) {
    return project.getComponent(ScalaCachesManagerImpl.class);
  }

  public ScalaCachesManagerImpl(Project project) {
    myProject = project;
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NotNull
  public String getComponentName() {
    return "Scala caches manager";
  }

  public void initComponent() {
    myCache = new ScalaShortNamesCacheImpl(myProject);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (!myProject.isDisposed()) {
              JavaPsiFacade.getInstance(myProject).registerShortNamesCache(getNamesCache());
            }
          }
        });
      }
    });

  }

  public void disposeComponent() {

  }

  public ScalaShortNamesCacheImpl getNamesCache() {
    return myCache;
  }
}
