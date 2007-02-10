package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.containers.*;
import com.intellij.ProjectTopics;

import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCaches;

/**
 * @author Ilya.Sergey
 */
public class ScalaSDKCachesManager implements ProjectComponent {

  private static final Logger LOG = Logger.getInstance(ScalaSDKCachesManager.class.getName());
  protected Map<ProjectJdk, ScalaFilesCache> sdk2ScalaFilesCache = new HashMap<ProjectJdk, ScalaFilesCache>();

  @NonNls
  private static final String SCALA_CACHE_DIR = "scala_caches";
  @NonNls
  private static final String SCALA_CACHE_FILE = "sdk";

  private ProjectJdkTable.Listener jdkTableListener;

  private Project myProject;


  public ScalaSDKCachesManager(@NotNull final Project project) {
    myProject = project;
    createListeners();
  }


  private void createListeners() {

    jdkTableListener = new ProjectJdkTable.Listener() {
      public void jdkAdded(final ProjectJdk sdk) {
        // Do nothing
      }

      public void jdkRemoved(final ProjectJdk sdk) {
        removeSDK(sdk, true);
      }

      public void jdkNameChanged(final ProjectJdk sdk, final String previousName) {
        renameSDK(sdk, previousName);
      }
    };
  }

  /**
   * @param sdk Sdk to get ScalaFilesStorage
   * @return FilesCache for sdk
   */
  public ScalaFilesCache getSdkFilesCache(final ProjectJdk sdk) {
    return sdk2ScalaFilesCache.get(sdk);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_SDK_CACHE_MANAGER;
  }

  public void initComponent() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        initSkdCaches(myProject);
        //  register listeners
        ProjectJdkTable.getInstance().addListener(jdkTableListener);
      }
    });
  }

  public void disposeComponent() {
    // unregistering listeners
    ProjectJdkTable.getInstance().removeListener(jdkTableListener);
    sdk2ScalaFilesCache.clear();
  }


  public void initSkdCaches(final Project project) {

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final Set<ProjectJdk> sdk2Add = new HashSet<ProjectJdk>();
    // Searching for all used sdk
    for (Module module : modules) {
      final ProjectJdk sdk = ModuleRootManager.getInstance(module).getJdk();
      if (sdk != null) {
        sdk2Add.add(sdk);
      }
    }
    // remove old sdk
    final Set<ProjectJdk> cachedSDKs = new HashSet<ProjectJdk>(sdk2ScalaFilesCache.keySet());
    for (ProjectJdk sdk : cachedSDKs) {
      if (!sdk2Add.contains(sdk)) {
        removeSDK(sdk, true);
      } else {
        sdk2Add.remove(sdk);
      }
    }
    // adding new Skds
    for (ProjectJdk modulesSDK : sdk2Add) {
      addSDK(modulesSDK);
    }
  }

  public Map<ProjectJdk, ScalaFilesCache> getSdkFilesCaches() {
    return sdk2ScalaFilesCache;
  }

  protected void removeSDK(@NotNull final ProjectJdk sdk, final boolean removeCacheFromDisk) {
    final ScalaFilesCache cache = sdk2ScalaFilesCache.remove(sdk);
    if (cache != null) {
      if (removeCacheFromDisk) {
        cache.removeCacheFile();
      }
    }
  }

  public void addSDK(@NotNull final ProjectJdk sdk) {
    final ScalaFilesCache newSdkCache = new ScalaFilesCacheImpl(myProject);
    newSdkCache.setCacheName(sdk.getName());
    newSdkCache.setCacheUrls(getSDKContentRootURLs(sdk));
    newSdkCache.setCacheFilePath(generateCacheFilePath(sdk));
    newSdkCache.init(true);
    sdk2ScalaFilesCache.put(sdk, newSdkCache);
  }

/**********************************************************************************************************************/
/************************************** Auxiliary methods *************************************************************/
/**********************************************************************************************************************/

  /**
   * @param sdk Jdk to get cached info for
   * @return Path to file where cached information is saved.
   */
  @NotNull
  private String generateCacheFilePath(@NotNull final ProjectJdk sdk) {
    return generateCacheFilePath(sdk, sdk.getName());
  }

  /**
   * @param sdk  Sdk to get cached info for
   * @param name Sdk name
   * @return Path to file where cached information is saved.
   */
  @NotNull
  private String generateCacheFilePath(@NotNull final ProjectJdk sdk, @NotNull final String name) {
    return PathManager.getSystemPath() + "/" + SCALA_CACHE_DIR + "/" + SCALA_CACHE_FILE + "/" + name + "_" + sdk.getHomePath().hashCode();
  }


  protected String[] getSDKContentRootURLs(@NotNull final ProjectJdk sdk) {
    return sdk.getRootProvider().getUrls(OrderRootType.SOURCES);
  }

  protected String generateCacheName(final ProjectJdk sdk) {
    return generateCacheName(sdk, sdk.getName());
  }

  protected String generateCacheName(final ProjectJdk sdk, final String sdkName) {
    return sdkName + "_" + sdk.getHomePath().hashCode();
  }

  protected void renameSDK(final ProjectJdk sdk, final String previousName) {
    final File prevDataFile = new File(generateCacheFilePath(sdk, previousName));
    final File newDataFile = new File(generateCacheFilePath(sdk));
    try {
      if (!prevDataFile.exists()) {
        return;
      }
      final File parentDir = prevDataFile.getParentFile();

      if (!newDataFile.exists()) {
        newDataFile.mkdirs();
      }

      if (!newDataFile.exists()) {
        LOG.warn("Can't create [" + newDataFile.getPath() + "]");
      }

      if (newDataFile.isDirectory()) {
        newDataFile.delete();
      }
      prevDataFile.renameTo(newDataFile);
      parentDir.delete();
    } catch (Exception e) {
      LOG.warn("Cache file [" + prevDataFile.getPath() + "] wasn't renamed to [" +
              newDataFile.getPath() + "]");
      LOG.warn(e);
    }
  }


}
