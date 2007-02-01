package org.jetbrains.plugins.scala.cache;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.HashSet;

import org.jetbrains.annotations.NotNull;

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


  private void refreshCache(boolean runProcessWithProgressSynchronously) {
    Runnable refreshCache = new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final Set<VirtualFile> filesToAdd = scanForFiles(myCacheUrls, progressIndicator);

        for (VirtualFile file : filesToAdd) {
          if (file.isValid()){
            final PsiManager myPsiManager = PsiManager.getInstance(myProject);
            PsiFile psiFile = myPsiManager.findFile(file);
          }
        }

        /**
         * Remove garbage
         */
        /*
        final VirtualFileManager fileManager = VirtualFileManager.getInstance();
        final List<RFileInfo> fileInfos = new LinkedList<RFileInfo>(myRFilesStorage.getAllRFileInfos());
        final Set<String> urls2Delete = new HashSet<String>();
        for (RFileInfo fileInfo : fileInfos) {
          final VirtualFile virtualFile = fileManager.findFileByUrl(fileInfo.getFileUrl());
          if (virtualFile == null || !filesToAdd.contains(virtualFile)) {
            urls2Delete.add(fileInfo.getFileUrl());
          }
        }

        removeRFileInfos(urls2Delete, progressIndicator);
        */
        /**
         * Add new files
         */
        /*addRFileInfos(filesToAdd, progressIndicator);
         */
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
