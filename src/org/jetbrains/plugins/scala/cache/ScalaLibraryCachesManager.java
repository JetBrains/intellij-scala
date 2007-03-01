package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.ScalaComponents;

/**
 * @author Ilya.Sergey
 */
public class ScalaLibraryCachesManager implements ProjectComponent {

  private static final Logger LOG = Logger.getInstance(ScalaSDKCachesManager.class.getName());
  protected Map<Library, ScalaFilesCache> libraryScalaFilesCache = new HashMap<Library, ScalaFilesCache>();

  @NonNls                                              
  private static final String SCALA_CACHE_DIR = "scala_caches";
  @NonNls
  private static final String SCALA_CACHE_FILE = "libraries";
  private Project myProject;
  private ProjectLibraryTable.Listener libraryTableListener;


  public ScalaLibraryCachesManager(@NotNull final Project project) {
    myProject = project;
    createListeners();
  }


  private void createListeners() {

    libraryTableListener = new ProjectLibraryTable.Listener() {

      public void afterLibraryAdded(Library newLibrary) {
      }

      public void afterLibraryRenamed(Library library) {
        removeLibrary(library, true);
        addLibrary(library);
      }

      public void beforeLibraryRemoved(Library library) {
      }

      public void afterLibraryRemoved(Library library) {
        removeLibrary(library, true);
      }
    };
  }

  /**
   * @param library Library to get ScalaFilesStorage
   * @return FilesCache for libraries
   */
  public ScalaFilesCache getSdkFilesCache(final Library library) {
    return libraryScalaFilesCache.get(library);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER;
  }

  public void initComponent() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        initLibraryCaches(myProject);
        //  register listeners
        ProjectLibraryTable.getInstance(myProject).addListener(libraryTableListener);
      }
    });
  }

  public void disposeComponent() {
    // unregistering listeners
    if (libraryTableListener != null && ProjectLibraryTable.getInstance(myProject) != null) {
      ProjectLibraryTable.getInstance(myProject).removeListener(libraryTableListener);
    }
    libraryScalaFilesCache.clear();

  }


  public void initLibraryCaches(final Project project) {

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final Set<Library> libraries2Add = new HashSet<Library>();
    // Searching for all used libraries
    for (Module module : modules) {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry) entry).getLibrary();
          if (library != null) {
            libraries2Add.add(library);
          }
        }
      }
    }
    // remove old libraries
    final Set<Library> cachedLibraries = new HashSet<Library>(libraryScalaFilesCache.keySet());
    for (Library library : cachedLibraries) {
      if (!libraries2Add.contains(library)) {
        removeLibrary(library, true);
      } else {
        libraries2Add.remove(library);
      }
    }
    // adding new Skds
    for (Library modulesLibrary : libraries2Add) {
      addLibrary(modulesLibrary);
    }
  }

  public Map<Library, ScalaFilesCache> getLibraryFilesCaches() {
    return libraryScalaFilesCache;
  }

  protected void removeLibrary(@NotNull final Library library, final boolean removeCacheFromDisk) {
    final ScalaFilesCache cache = libraryScalaFilesCache.remove(library);
    if (cache != null) {
      if (removeCacheFromDisk) {
        cache.removeCacheFile();
      }
    }
  }

  public void addLibrary(@NotNull final Library library) {
    if (library != null) {
      final ScalaFilesCache newLibraryCache = new ScalaFilesCacheImpl(myProject);
      newLibraryCache.setCacheName(library.getName());
      newLibraryCache.setCacheUrls(getLibraryContentRootURLs(library));
      newLibraryCache.setCacheFilePath(generateCacheFilePath(library));
      newLibraryCache.init(true);
      libraryScalaFilesCache.put(library, newLibraryCache);
    }
  }

/**********************************************************************************************************************/
/************************************** Auxiliary methods *************************************************************/
/**********************************************************************************************************************/

  /**
   * @param library Library to get cached info for
   * @return Path to file where cached information is saved.
   */
  @NotNull
  private String generateCacheFilePath(@NotNull final Library library) {
    return generateCacheFilePath(library, library.getName());
  }

  /**
   * @param library Library to get cached info for
   * @param name    Sdk name
   * @return Path to file where cached information is saved.
   */
  @NotNull
  private String generateCacheFilePath(@NotNull final Library library, @NotNull final String name) {
    return PathManager.getSystemPath() + "/" + SCALA_CACHE_DIR + "/" + SCALA_CACHE_FILE + "/" + name;
  }


  protected String[] getLibraryContentRootURLs(@NotNull final Library library) {
    return library.getRootProvider().getUrls(OrderRootType.SOURCES);
  }

  protected String generateCacheName(final Library library) {
    return generateCacheName(library, library.getName());
  }

  protected String generateCacheName(final Library library, final String libraryName) {
    return libraryName + "_" + library.hashCode();
  }

  protected void renameLibrary(@NotNull final Library library, final String previousName) {
    final File prevDataFile = new File(generateCacheFilePath(library, previousName));
    final File newDataFile = new File(generateCacheFilePath(library));
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
