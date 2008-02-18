/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.cache.module;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.ScalaFilesCacheImpl;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.listeners.ScalaPsiTreeListener;
import org.jetbrains.plugins.scala.util.ScalaUtils;

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
//    ((VirtualFileManager) VirtualFileManager.getInstance()).registerRefreshUpdater(myCacheUpdater);
  }

  private void unregisterCacheUpdater() {
    if (myCacheUpdater != null) {
      ProjectRootManagerEx.getInstanceEx(myProject).unregisterChangeUpdater(myCacheUpdater);
//      ((VirtualFileManager) VirtualFileManager.getInstance()).unregisterRefreshUpdater(myCacheUpdater);
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