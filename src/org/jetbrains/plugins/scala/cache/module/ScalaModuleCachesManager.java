package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExternalChangeAction;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiManagerConfiguration;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.ide.errorTreeView.SimpleMessageElement;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.ProjectTopics;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.ScalaFilesCache;
import org.jetbrains.plugins.scala.cache.ScalaSDKCachesManager;
import org.jetbrains.plugins.scala.cache.ScalaLibraryCachesManager;
import org.jetbrains.plugins.scala.gotoclass.ScalaChooseByNameContributor;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

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
  private Map<Module, ScalaFilesCache> moduleScalaFilesCache = new HashMap<Module, ScalaFilesCache>();

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
        moduleScalaFilesCache = new HashMap<Module, ScalaFilesCache>();
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
    if (rootConnection != null) {
      rootConnection.disconnect();
    }
    myModuleFilesCache.saveCacheToDisk(true);
  }

}
