package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.ProjectTopics;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import org.jetbrains.plugins.scala.cache.ScalaFilesCache;
import org.jetbrains.plugins.scala.cache.ScalaSDKCachesManager;
import org.jetbrains.plugins.scala.cache.ScalaLibraryCachesManager;
import org.jetbrains.plugins.scala.gotoclass.ScalaChooseByNameContributor;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Ilya.Sergey
 */
public class ScalaModuleCachesManager implements ModuleComponent {

  private static final Logger LOG = Logger.getInstance(ScalaModuleCachesManager.class.getName());

  @NonNls
  private static final String SCALA_CACHE_DIR = "scala_caches";
  @NonNls
  private static final String SCALA_CACHE_FILE = "module";
  private Module myModule;
  private ModuleRootListener moduleRootListener;
  private MessageBusConnection rootConnection;

  private ScalaModuleCaches myModuleFilesCache;
  private ScalaFilesCache sdkScalaFilesCache;
  private Map<Library, ScalaFilesCache> libraryScalaFilesCache = new HashMap<Library, ScalaFilesCache>();

  private ScalaSDKCachesManager sdkCachesManager;
  private ScalaLibraryCachesManager libraryCachesManager;


  public ScalaModuleCachesManager(@NotNull final Module module) {
    myModule = module;
    createListeners();
  }

  public ScalaModuleCaches getModuleFilesCache() {
    return myModuleFilesCache;
  }

  public ScalaFilesCache getModuleSdkFilesCache() {
    return sdkScalaFilesCache;
  }

  @NotNull
  private String generateCacheFilePath() {
    return PathManager.getSystemPath() + "/" + SCALA_CACHE_DIR + "/" +
            SCALA_CACHE_FILE + "/" + myModule.getProject().getName() +
            "/" + myModule.getName() + "_" + myModule.getModuleFilePath().hashCode();
  }

  private void createListeners() {
    moduleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {

        libraryScalaFilesCache = new HashMap<Library, ScalaFilesCache>();
        sdkScalaFilesCache = null;

        sdkCachesManager.initSkdCaches(myModule.getProject());
        libraryCachesManager.initLibraryCaches(myModule.getProject());

        OrderEntry[] entries = ModuleRootManager.getInstance(myModule).getOrderEntries();
        for (OrderEntry entry : entries) {
          if (entry instanceof JdkOrderEntry) {
            processJdkEntry((JdkOrderEntry) entry);
          } else if (entry instanceof LibraryOrderEntry) {
            processLibraryEntry((LibraryOrderEntry) entry);
          } else if (entry instanceof LibraryOrderEntry) {
            processModuleEntry((ModuleOrderEntry) entry);
          }
        }
      }
    };
  }

  protected void processJdkEntry(JdkOrderEntry entry) {
    sdkScalaFilesCache = sdkCachesManager.getSdkFilesCaches().get(entry.getJdk());
  }


  protected void processLibraryEntry(LibraryOrderEntry entry) {
    libraryScalaFilesCache.put(entry.getLibrary(),
            libraryCachesManager.getLibraryFilesCaches().get(entry.getLibrary()));
  }

  protected void processModuleEntry(ModuleOrderEntry entry) {
    // Do nothing
  }


  public void projectOpened() {
  }

  public void projectClosed() {
  }

  /**
   * Creating of refresging cache
   */
  public void moduleAdded() {

    myModuleFilesCache = new ScalaModuleCachesImpl(myModule);
    myModuleFilesCache.setCacheUrls(ScalaUtils.getModuleRootUrls(myModule));
    myModuleFilesCache.setCacheName(myModule.getName());
    myModuleFilesCache.setCacheFilePath(generateCacheFilePath());

    StartupManager.getInstance(myModule.getProject()).runPostStartup(new Runnable() {
      public void run() {
        myModuleFilesCache.init(true);
      }
    });

    ChooseByNameRegistry registry = ChooseByNameRegistry.getInstance();
    registry.contributeToClasses(new ScalaChooseByNameContributor());
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_CACHE_MANAGER;
  }

  public void initComponent() {

    sdkCachesManager = ((ScalaSDKCachesManager) myModule.getProject().getComponent(ScalaComponents.SCALA_SDK_CACHE_MANAGER));
    libraryCachesManager = ((ScalaLibraryCachesManager) myModule.getProject().getComponent(ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER));

    StartupManager.getInstance(myModule.getProject()).registerPostStartupActivity(new Runnable() {
      public void run() {
        //  register listeners
        rootConnection = myModule.getProject().getMessageBus().connect();
        rootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, moduleRootListener);
      }
    });
  }

  public void disposeComponent() {
    myModuleFilesCache.dispose();
    if (rootConnection != null) {
      rootConnection.disconnect();
    }
    myModuleFilesCache.saveCacheToDisk(true);
  }

}
