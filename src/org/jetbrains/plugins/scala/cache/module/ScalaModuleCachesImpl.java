package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.pom.PomModel;
import com.intellij.psi.PsiManager;
import org.jetbrains.plugins.scala.cache.ScalaFilesCacheImpl;
import org.jetbrains.plugins.scala.cache.listeners.ScalaPsiTreeListener;
import org.jetbrains.plugins.scala.cache.listeners.ScalaVirtualFileListener;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Ilya.Sergey
 */
public class ScalaModuleCachesImpl extends ScalaFilesCacheImpl implements ScalaModuleCaches {

  private Module myModule;

  private Set<String> myOutOfDateFileUrls = Collections.synchronizedSet(new HashSet<String>());

  private ScalaFilesCacheUpdater myCacheUpdater;

  public ScalaModuleCachesImpl(Module module) {
    super(module.getProject());
    myModule = module;
  }

  public void init(boolean b) {
    super.init(b);
    registerCacheUpdater();
    registerListeners();
  }

  public synchronized ScalaFileInfo getUp2DateFileInfo(@NotNull final VirtualFile file) {
    if (myOutOfDateFileUrls.remove(file.getUrl())) {
      return regenerateFileInfo(file);
    }
    return super.getUp2DateFileInfo(file, true);
  }

  private void registerListeners() {
    PsiManager.getInstance(myModule.getProject()).addPsiTreeChangeListener(
            new ScalaPsiTreeListener(this)
    );
  }

  private void unregisterListeners() {
    PsiManager.getInstance(myModule.getProject()).removePsiTreeChangeListener(
            new ScalaPsiTreeListener(this)
    );
  }

  public synchronized void processFileDeleted(final @NotNull String url) {
    myOutOfDateFileUrls.remove(url);
    removeScalaFileInfo(url);
  }


  public synchronized void processFileChanged(final @NotNull VirtualFile file) {
    if (ScalaUtils.isScalaFile(file)) {
      myOutOfDateFileUrls.add(file.getUrl());
    }
  }

  public boolean isOutOfDate(String url) {
    return myOutOfDateFileUrls.contains(url);
  }

  public synchronized void refresh() {
    for (Iterator<String> iterator = myOutOfDateFileUrls.iterator(); iterator.hasNext();) {
      String url = iterator.next();
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      iterator.remove();
      regenerateFileInfo(file);
    }
  }


  public void dispose() {
    super.dispose();
    unregisterCacheUpdater();
    unregisterListeners();
  }


  private void registerCacheUpdater() {
    myCacheUpdater = new ScalaFilesCacheUpdater();
    ProjectRootManagerEx.getInstanceEx(myProject).registerChangeUpdater(myCacheUpdater);
    ((VirtualFileManagerEx) VirtualFileManagerEx.getInstance()).registerRefreshUpdater(myCacheUpdater);
  }

  private void unregisterCacheUpdater() {
    if (myCacheUpdater != null) {
      ProjectRootManagerEx.getInstanceEx(myProject).unregisterChangeUpdater(myCacheUpdater);
      ((VirtualFileManagerEx) VirtualFileManagerEx.getInstance()).unregisterRefreshUpdater(myCacheUpdater);
    }
  }


  class ScalaFilesCacheUpdater implements CacheUpdater {

    public synchronized VirtualFile[] queryNeededFiles() {
      List<VirtualFile> files = new ArrayList<VirtualFile>();
      for (String outOfDateFileUrl : myOutOfDateFileUrls) {
        final VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(outOfDateFileUrl);
        if (vFile != null) {
          files.add(vFile);
        }
      }
      return files.toArray(new VirtualFile[files.size()]);
    }

    public synchronized void processFile(FileContent fileContent) {
      final VirtualFile virtualFile = fileContent.getVirtualFile();
      getUp2DateFileInfo(virtualFile);
      final String url = virtualFile.getUrl();
      myOutOfDateFileUrls.remove(url);
    }

    public void updatingDone() {
     // assert myOutOfDateFileUrls.size() == 0;
    }

    public void canceled() {
      // do nothing
    }
  }


}