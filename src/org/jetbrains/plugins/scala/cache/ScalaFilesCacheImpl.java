package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.*;
import com.intellij.lang.StdLanguages;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.impl.StartupManagerImpl;

import java.util.*;
import java.io.*;
import java.lang.reflect.Array;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.info.ScalaInfoFactory;
import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.cache.info.impl.ScalaFilesStorageImpl;
import org.jetbrains.plugins.scala.ScalaBundle;

/**
 * @author Ilya.Sergey
 */
public class ScalaFilesCacheImpl implements ScalaFilesCache {

  protected final Project myProject;
  private String[] myCacheUrls;
  private String myCacheName;
  private String myCacheDataFilePath;

  private ScalaFilesStorage myScalaFilesStorage;

  public ScalaFilesCacheImpl(final Project project) {
    myProject = project;
  }

  public void setCacheName(String myCacheName) {
    this.myCacheName = myCacheName;
  }

  public String getCacheName() {
    return myCacheName;
  }

  /**
   * Initialisation of cache
   *
   * @param runProcessWithProgressSynchronously
   *
   */
  public void init(final boolean runProcessWithProgressSynchronously) {

    boolean creatingCache = false;

    myScalaFilesStorage = loadCacheFromDisk(runProcessWithProgressSynchronously);

    if (myScalaFilesStorage == null) {
      creatingCache = true;
      myScalaFilesStorage = new ScalaFilesStorageImpl();
    }

    // Updating file cache, if some files are out of date
    refreshCache(creatingCache, runProcessWithProgressSynchronously);

    // Save updated cache on disk
    saveCacheToDisk(runProcessWithProgressSynchronously);

  }

  public void dispose() {

  }

  public void setCacheUrls(@NotNull String[] myCacheUrls) {
    this.myCacheUrls = myCacheUrls;
  }

  /**
   * Returns information about all files in module
   *
   * @return
   */
  public Collection<ScalaFileInfo> getAllClasses() {
    return myScalaFilesStorage.getAllScalaFileInfos();
  }

  /**
   * Refreshes cache and may create new instanse if is not created yet
   *
   * @param creatingCache Create or not
   * @param runProcessWithProgressSynchronously
   *
   */
  private void refreshCache(final boolean creatingCache, boolean runProcessWithProgressSynchronously) {
    Runnable refreshCache = new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final Set<VirtualFile> filesToAdd = scanForFiles(myCacheUrls, progressIndicator);

        /**
         * Remove garbage
         */
        final VirtualFileManager fileManager = VirtualFileManager.getInstance();
        final List<ScalaFileInfo> fileInfos = new LinkedList<ScalaFileInfo>(myScalaFilesStorage.getAllScalaFileInfos());
        final Set<String> urls2Delete = new HashSet<String>();
        for (ScalaFileInfo fileInfo : fileInfos) {
          final VirtualFile virtualFile = fileManager.findFileByUrl(fileInfo.getFileUrl());
          if (virtualFile == null || !filesToAdd.contains(virtualFile)) {
            urls2Delete.add(fileInfo.getFileUrl());
          }
        }
        removeScalaFileInfos(urls2Delete, progressIndicator);
        addScalaFileInfos(filesToAdd, progressIndicator);
      }
    };

    if (runProcessWithProgressSynchronously) {
      final String message = creatingCache ? ScalaBundle.message("title.cache.creating", getCacheName()) : ScalaBundle.message("title.cache.updating", getCacheName());
      ProgressManager.getInstance().runProcessWithProgressSynchronously(refreshCache, message, false, myProject);
    } else {
      refreshCache.run();
    }
  }

  /**
   * Adding new info about files
   *
   * @param filesToAdd
   * @param progressIndicator
   */
  private void addScalaFileInfos(Set<VirtualFile> filesToAdd, ProgressIndicator progressIndicator) {
    int count = 0;
    int size = filesToAdd.size();
    if (progressIndicator != null) {
      progressIndicator.setText(ScalaBundle.message("title.cache.files.parsing"));
    }
    for (VirtualFile file : filesToAdd) {
      if (progressIndicator != null) {
        progressIndicator.setFraction((1.0 * (++count)) / size);
        progressIndicator.setText2(file.getPresentableUrl());
      }
// getting file infos with force updates
      getUp2DateFileInfo(file, true);
    }
  }


  private void removeScalaFileInfos(@NotNull Set<String> urlsToDelete, final ProgressIndicator progressIndicator) {
    if (progressIndicator != null) {
      progressIndicator.setText(ScalaBundle.message("title.cache.files.removing"));
    }
    for (String url : urlsToDelete) {
      myScalaFilesStorage.removeScalaInfo(url);
    }
  }


  /**
   * @param file        File for what restoring cached information
   * @param forceUpdate Force update or not
   * @return Old info about file if not changed or new whil regenerateFileInfo(file)
   */
  protected ScalaFileInfo getUp2DateFileInfo(VirtualFile file, final boolean forceUpdate) {
    final ScalaFileInfo fileInfo = myScalaFilesStorage.getScalaFileInfoByFileUrl(file.getUrl());
    if (fileInfo != null && fileInfo.getFileTimestamp() == file.getTimeStamp()) {
      return fileInfo;
    }
    if (!forceUpdate && fileInfo != null) {
      return fileInfo;
    } else {
      return regenerateFileInfo(file);
    }
  }

  /**
   * Generates cabout file
   *
   * @param file
   * @return final boolean forceUpdate
   */
  protected ScalaFileInfo regenerateFileInfo(VirtualFile file) {
    final String url = file.getUrl();
    removeScalaFileInfo(url);
    return createScalaFileInfo(file);
  }


  /**
   * Removes ScalaFileInfo
   *
   * @param fileUrl url for file
   */
  protected void removeScalaFileInfo(@Nullable final String fileUrl) {
    myScalaFilesStorage.removeScalaInfo(fileUrl);
  }


  /**
   * Creates ScalaFileInfo for file according corresponding ScalaFile
   *
   * @param file VirtualFile
   * @return new RFileInfo for file or null.
   */
  public ScalaFileInfo createScalaFileInfo(@NotNull final VirtualFile file) {
    final ScalaFileInfo fileInfo = ScalaInfoFactory.createScalaFileInfo(myProject, file);
    if (fileInfo == null) {
      return null;
    }
    myScalaFilesStorage.addScalaInfo(fileInfo);
    return fileInfo;
  }


  /**
   * Scanning for new files
   *
   * @param progressIndicator
   */
  private Set<VirtualFile> scanForFiles(@NotNull final String[] urls, final ProgressIndicator progressIndicator) {
    VirtualFileManager fManager = VirtualFileManager.getInstance();
    final Set<VirtualFile> filesToAdd = new HashSet<VirtualFile>();
    for (String url : urls) {
      final VirtualFile root = fManager.findFileByUrl(url);
      if (progressIndicator != null & root != null) {
        progressIndicator.setText(ScalaBundle.message("title.cache.files.scanning", root.getPresentableUrl()));
      }
      ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      VirtualFileScanner.addScalaFiles(root, filesToAdd, index);
    }
    return filesToAdd;
  }


  public void setCacheFilePath(@NotNull final String dataFileUrl) {
    this.myCacheDataFilePath = dataFileUrl;
  }

  /**
   * Saves serialized cache data to dataFile
   *
   * @param runProcessWithProgressSynchronously
   *         If is true update operaiton
   *         will be run in a background thread and will show a modal progress dialog in
   *         the main thread while. Otherwise will be run in current thread without
   *         any modal dialogs..
   */
  public void saveCacheToDisk(final boolean runProcessWithProgressSynchronously) {
    final File dataFile = new File(myCacheDataFilePath);
    final Runnable saveCache = new Runnable() {
      public void run() {
        final ProgressIndicator indicator =
                ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(ScalaBundle.message("title.please.wait"));
        }
        try {
          if (!dataFile.exists()) {
            dataFile.mkdirs();
          }
          if (!dataFile.exists()) {
            return;
          }
          if (dataFile.isDirectory()) {
            dataFile.delete();
          }
          dataFile.createNewFile();
          ObjectOutputStream oos = null;
          try {
            oos = new ObjectOutputStream(new BufferedOutputStream(
                    new FileOutputStream(dataFile)));
            oos.writeObject(myScalaFilesStorage);
          }
          finally {
            if (oos != null) {
              oos.close();
            }
          }
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    };

    if (runProcessWithProgressSynchronously) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(saveCache, ScalaBundle.message("title.cache.saving", myCacheName), false, myProject);
    } else {
      saveCache.run();
    }
  }

  /**
   * Returns instance of PsiClass by qualified qualifiedClassName
   *
   * @param qualifiedClassName - Qualified class qualifiedClassName
   * @return
   */
  public PsiClass getClassByName(@NotNull final String qualifiedClassName) {
    PsiClass[] classes = getClassesInFileByClassName(qualifiedClassName);
    for (PsiClass clazz : classes) {
      if (clazz.getQualifiedName().equals(qualifiedClassName)) {
        return clazz;
      }
    }
    return null;
  }

  public PsiClass[] getClassesByName(@NotNull final String qualifiedClassName) {
    PsiClass[] classes = getClassesInFileByClassName(qualifiedClassName);
    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    for (PsiClass clazz : classes) {
      if (qualifiedClassName.equals(clazz.getQualifiedName())) {
        acc.add(clazz);
      }
    }
    return acc.toArray(PsiClass.EMPTY_ARRAY);
  }

  /**
   * @return qualified names of all classes in current cache
   */
  public Collection<String> getAllClassNames() {
    return myScalaFilesStorage.getAllClassNames();
  }


  /**
   * @return short names of all classes in current cache
   */
  public Collection<String> getAllClassShortNames() {
    return myScalaFilesStorage.getAllClassShortNames();
  }


  /**
   * Return all classes in file, in which contains current class
   *
   * @param qualifiedClassName
   * @return
   */
  private PsiClass[] getClassesInFileByClassName(@NotNull String qualifiedClassName) {
    String url = myScalaFilesStorage.getFileUrlByClassName(qualifiedClassName);
    VirtualFile file = VirtualFileScanner.getFileByUrl(url);
    final PsiManager myPsiManager = PsiManager.getInstance(myProject);
    if (file != null) {
      PsiFile psiFile = myPsiManager.findFile(file);
      FileViewProvider provider = psiFile.getViewProvider();
      PsiJavaFile javaPsi = (PsiJavaFile) provider.getPsi(StdLanguages.JAVA);
      return javaPsi.getClasses();
    }
    return new PsiClass[0];
  }


  /**
   * Return all classes by short name
   *
   * @param shortName
   * @return
   */
  public PsiClass[] getClassesByShortClassName(@NotNull String shortName) {
    String[] urls = myScalaFilesStorage.getFileUrlsByShortClassName(shortName);

    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    for (String url : urls) {
      VirtualFile file = VirtualFileScanner.getFileByUrl(url);
      final PsiManager myPsiManager = PsiManager.getInstance(myProject);
      if (file != null) {
        PsiFile psiFile = myPsiManager.findFile(file);
        FileViewProvider provider = psiFile.getViewProvider();
        PsiJavaFile javaPsi = (PsiJavaFile) provider.getPsi(StdLanguages.JAVA);
        PsiClass classes[] = javaPsi.getClasses();
        for (PsiClass clazz : classes) {
          if (shortName.equals(clazz.getName())) {
            acc.add(clazz);
          }
        }
      }
    }
    return acc.toArray(PsiClass.EMPTY_ARRAY);

  }

  /**
   * Tries to load cache data from disk!
   *
   * @param runProcessWithProgressSynchronously
   *         show modal dialog or no
   * @return RFilesStorage object - if something loaded, null otherwise
   */
  @Nullable
  private ScalaFilesStorage loadCacheFromDisk(final boolean runProcessWithProgressSynchronously) {
    final File moduleDataFile = new File(myCacheDataFilePath);
    final ScalaFilesStorage[] storage = new ScalaFilesStorage[]{null};
    final Runnable loadCacheRunnable = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(ScalaBundle.message("title.please.wait"));
          indicator.setText2(ScalaBundle.message("title.cache.datafile.loading"));
        }
        if (!moduleDataFile.exists()) {
          return;
        }
        try {
          ObjectInputStream ois = null;
          try {
            ois = new ObjectInputStream(new BufferedInputStream(
                    new FileInputStream(moduleDataFile)));
            final Object data = ois.readObject();
            if (data instanceof ScalaFilesStorage) {
              final ScalaFilesStorage info = (ScalaFilesStorage) data;
              storage[0] = info;
            }
          }
          finally {
            if (ois != null) {
              ois.close();
            }
          }
        }
        catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    };

    if (runProcessWithProgressSynchronously) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(loadCacheRunnable,
              ScalaBundle.message("title.cache.loading", myCacheName), false, myProject);
    } else {
      loadCacheRunnable.run();
    }
    return storage[0];
  }


  public void removeCacheFile() {
    final File dataFile = new File(myCacheDataFilePath);
    try {
      if (dataFile.exists()) {
        final File parentDir = dataFile.getParentFile();
        dataFile.delete();
        parentDir.delete();
      }
    } catch (SecurityException e) {
      e.printStackTrace();
    }
  }

  class StartupCacheUpdater implements CacheUpdater {

    public VirtualFile[] queryNeededFiles() {
      final Set<VirtualFile> filesToAdd = scanForFiles(myCacheUrls, null);
      /**
       * Remove garbage
       */
      final VirtualFileManager fileManager = VirtualFileManager.getInstance();
      final List<ScalaFileInfo> fileInfos = new LinkedList<ScalaFileInfo>(myScalaFilesStorage.getAllScalaFileInfos());
      final Set<String> urls2Delete = new HashSet<String>();
      for (ScalaFileInfo fileInfo : fileInfos) {
        final VirtualFile virtualFile = fileManager.findFileByUrl(fileInfo.getFileUrl());
        if (virtualFile == null || !filesToAdd.contains(virtualFile)) {
          urls2Delete.add(fileInfo.getFileUrl());
        }
      }
      removeScalaFileInfos(urls2Delete, null);
      return filesToAdd.toArray(VirtualFile.EMPTY_ARRAY);
    }

    public void processFile(FileContent fileContent) {
      getUp2DateFileInfo(fileContent.getVirtualFile(), true);
    }

    public void updatingDone() {
      saveCacheToDisk(true);
    }

    public void canceled() {

    }
  }


}
