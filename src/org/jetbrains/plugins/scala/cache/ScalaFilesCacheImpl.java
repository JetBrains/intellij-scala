package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.project.Project;

import java.util.Set;
import java.util.HashSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.info.ScalaInfoFactory;

/**
 * @author Ilya.Sergey
 */
public class ScalaFilesCacheImpl implements ScalaFilesCache {

  protected final Project myProject;
  private String[] myCacheUrls;

  public ScalaFilesCacheImpl(final Project project) {
    myProject = project;
  }

  public void init(boolean b) {
    refreshCache(true);
  }

  public void setCacheUrls(@NotNull String[] myCacheUrls) {
    this.myCacheUrls = myCacheUrls;
  }

  /**
   * Refreshes cache
   *
   * @param runProcessWithProgressSynchronously
   *
   */
  private void refreshCache(boolean runProcessWithProgressSynchronously) {
    Runnable refreshCache = new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final Set<VirtualFile> filesToAdd = scanForFiles(myCacheUrls, progressIndicator);

        addScalaFileInfos(filesToAdd, progressIndicator);

      }
    };

    if (runProcessWithProgressSynchronously) {
//      final String message = creatingCache ? RBundle.message("title.cache.creating", myCacheName) : RBundle.message("title.cache.updating", myCacheName);
      final String message = "Creating caches";
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
      //progressIndicator.setText(RBundle.message("title.cache.files.parsing"));
      progressIndicator.setText("Parsing new scala files");
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

  private ScalaFileInfo getUp2DateFileInfo(VirtualFile file, boolean b) {
    /*
    final RFileInfo fileInfo = myRFilesStorage.getRFileInfoByFileUrl(file.getUrl());
        if (fileInfo!=null && fileInfo.getFileTimestamp() == file.getTimeStamp()) {
            return fileInfo;
        }
        if (!forceUpdate && fileInfo!=null){
            return fileInfo;
        }
      */
    return regenerateFileInfo(file);
  }

  private ScalaFileInfo regenerateFileInfo(VirtualFile file) {
//    final String url = file.getUrl();
//    removeRFileInfo(url);
    return createScalaFileInfo(file);
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
/*
        myRFilesStorage.addRInfo(fileInfo);
        if (myWordsIndex!=null){
            myWordsIndex.addFileInfoToIndex(fileInfo);
        }
*/
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
        progressIndicator.setText("Processing " + root.getPresentableUrl());
        //progressIndicator.setText(RBundle.message("title.cache.files.scanning", root.getPresentableUrl()));
      }
      VirtualFileScanner.addScalaFiles(root, filesToAdd);
    }

    return filesToAdd;
  }

}
