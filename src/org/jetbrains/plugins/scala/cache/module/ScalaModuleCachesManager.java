package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.util.ScalaUtils;

/**
 * @author Ilya.Sergey
 */
public class ScalaModuleCachesManager implements ModuleComponent {


  @NonNls
  private static final String SCALA_CACHE_DIR = "scala_caches";
  @NonNls
  private static final String SCALA_CACHE_FILE = "module";
  private Module myModule;
  private ScalaModuleCaches myModuleFilesCache;


  public ScalaModuleCachesManager(@NotNull final Module module) {
    myModule = module;
  }

  @NotNull
  private String generateCacheFilePath() {
    return PathManager.getSystemPath() + "/" + SCALA_CACHE_DIR + "/" +
            SCALA_CACHE_FILE + "/" + myModule.getProject().getName() +
            "/" + myModule.getName() + "_" + myModule.getModuleFilePath().hashCode();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {

    myModuleFilesCache = new ScalaModuleCachesImpl(myModule);
    myModuleFilesCache.setCacheUrls(new String[]{ScalaUtils.getModuleRoot(myModule).getUrl()});

    StartupManager.getInstance(myModule.getProject()).runPostStartup(new Runnable() {
      public void run() {
        myModuleFilesCache.init(true);
      }
    });
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_CACHE_MANAGER;
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
