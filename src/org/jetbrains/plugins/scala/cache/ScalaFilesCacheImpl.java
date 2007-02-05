package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.project.Project;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

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

    // TODO implement serialization
    //myScalaFilesStorage = loadCacheFromDisk(runProcessWithProgressSynchronously);
    myScalaFilesStorage = null;
    if (myScalaFilesStorage == null) {
      creatingCache = true;
      myScalaFilesStorage = new ScalaFilesStorageImpl();
    }

// Updating file cache, if some files are out of date

    refreshCache(creatingCache, runProcessWithProgressSynchronously);

// Save updated cache on disk

    // TODO implement serialization
    //saveCacheToDisk(runProcessWithProgressSynchronously);
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

}
