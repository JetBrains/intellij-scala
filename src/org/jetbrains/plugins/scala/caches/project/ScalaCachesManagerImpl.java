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

package org.jetbrains.plugins.scala.caches.project;

import com.intellij.ProjectTopics;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;
import org.jetbrains.plugins.scala.caches.listeners.ScalaVirtualFileListener;
import org.jetbrains.plugins.scala.caches.module.ScalaFilesCacheImpl;
import org.jetbrains.plugins.scala.gotoclass.ScalaChooseByNameContributor;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.*;

/**
 * @author ilyas
 */
public class ScalaCachesManagerImpl extends ScalaCachesManager {

  private static final Logger LOG = Logger.getInstance(ScalaCachesManagerImpl.class.getName());

  @NonNls
  private static final String SCALA_CACHE_DIR = "scala_caches";
  @NonNls
  private static final String SCALA_CACHE_FILE = "project";
  private Project myProject;
  private ModuleRootListener myModuleRootListener;
  private MessageBusConnection myRootConnection;
  private ScalaVirtualFileListener myVFListener;

  private HashMap<Module, ScalaFilesCache> myModuleFilesCachesMap = new HashMap<Module, ScalaFilesCache>();

  public ScalaCachesManagerImpl(@NotNull final Project project) {
    myProject = project;
    createListeners();
  }

  public ScalaFilesCache getModuleFilesCache(Module module) {
    return myModuleFilesCachesMap.get(module);
  }

  public PsiClass getClassByName(String qName, GlobalSearchScope scope) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (final Module module : modules) {
      if (scope.isSearchInModuleContent(module)) {
        final ScalaFilesCache cache = getModuleFilesCache(module);
        if (cache != null) {
          PsiClass typeDef = cache.getClassByName(qName);
          if (typeDef != null) return typeDef;
        }
      }
    }

    return null;
  }

  public PsiClass[] getClassesByName(String qName, GlobalSearchScope scope) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final Module module : modules) {
      if (scope.isSearchInModuleContent(module)) {
        PsiClass[] typeDefs = getModuleFilesCache(module).getClassesByName(qName);
        result.addAll(Arrays.asList(typeDefs));
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public PsiClass[] getDeriverCandidates(PsiClass baseClass, GlobalSearchScope scope) {
    final String baseName = baseClass.getName();
    if (baseName != null) {
      List<PsiClass> result = new ArrayList<PsiClass>();
      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (final Module module : modules) {
        if (scope.isSearchInModuleContent(module)) {
          result.addAll(getModuleFilesCache(module).getDeriverCandidatess(baseName));
        }
      }

      return result.toArray(new PsiClass[result.size()]);
    }

    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  private String generateProjectCacheFilePath() {
    return PathManager.getSystemPath() + "/" + SCALA_CACHE_DIR + "/" +
            SCALA_CACHE_FILE + "/" + getProject().getName() + "_" +
            getProject().getPresentableUrl().hashCode();

  }

  @NotNull
  private String generateModuleCacheFilePath(Module module) {
    return generateProjectCacheFilePath() + "/" + module.getName() + "_" +
            module.getModuleFilePath().hashCode();
  }

  private void createListeners() {

    myVFListener = new ScalaVirtualFileListener(this);

    myModuleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {

      }

      public void rootsChanged(final ModuleRootEvent event) {
        initializeCaches();
      }

    };
  }

  protected void initializeCaches() {
    ModuleManager manager = ModuleManager.getInstance(getProject());

    Set<Module> modules = new com.intellij.util.containers.HashSet<Module>();
    for (Module module : manager.getModules()) {
      initializeCache(module);
      modules.add(module);
    }

    for (Iterator<Map.Entry<Module, ScalaFilesCache>> it = myModuleFilesCachesMap.entrySet().iterator(); it.hasNext();)
    {
      Map.Entry<Module, ScalaFilesCache> entry = it.next();
      Module module = entry.getKey();
      if (!modules.contains(module)) {
        entry.getValue().dispose();
        it.remove();
      }
    }
  }

  private void initializeCache(Module module) {
    ScalaFilesCache cache = myModuleFilesCachesMap.get(module);
    if (cache == null) {
      cache = new ScalaFilesCacheImpl(module);
      myModuleFilesCachesMap.put(module, cache);
      cache.init(module.getName(), generateModuleCacheFilePath(module));
    }

    cache.setCacheUrls(ScalaUtils.getModuleRootUrls(module));
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScalaCachesManager";
  }

  public void initComponent() {


    initializeCaches();

    CacheUpdater cacheUpdater = new MyCacheUpdater();

    StartupManager startupManager = StartupManager.getInstance(getProject());

    startupManager.getFileSystemSynchronizer().registerCacheUpdater(cacheUpdater);
    ((ProjectRootManagerEx) ProjectRootManager.getInstance(getProject())).registerChangeUpdater(cacheUpdater);

    ((StartupManagerEx) startupManager).registerPreStartupActivity(new Runnable() {
      public void run() {
        initializeCaches();
        VirtualFileManager.getInstance().addVirtualFileListener(myVFListener);
      }
    });

    myRootConnection = getProject().getMessageBus().connect();
    myRootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, myModuleRootListener);

    startupManager.registerPostStartupActivity(new Runnable() {
      public void run() {
        ChooseByNameRegistry registry = ChooseByNameRegistry.getInstance();
        registry.contributeToClasses(new ScalaChooseByNameContributor());

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            JavaPsiFacade.getInstance(myProject).registerShortNamesCache(new ScalaShortNamesCache(myProject));
          }
        });
      }
    });
  }

  public void disposeComponent() {
    for (ScalaFilesCache cache : myModuleFilesCachesMap.values()) {
      if (cache != null) {
        cache.dispose();
        cache.saveCacheToDisk();
      }
    }
    //  unregister listeners
    if (myRootConnection != null) {
      myRootConnection.disconnect();
    }

    if (myVFListener != null) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myVFListener);
    }

  }

  public Project getProject() {
    return myProject;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  private class MyCacheUpdater implements CacheUpdater {
    private Map<VirtualFile, CacheUpdater> myFilesToCacheUpdaters;

    public VirtualFile[] queryNeededFiles() {
      myFilesToCacheUpdaters = new HashMap<VirtualFile, CacheUpdater>();
      for (ScalaFilesCache cache : myModuleFilesCachesMap.values()) {
        CacheUpdater updater = cache.getCacheUpdater();
        VirtualFile[] hisFiles = updater.queryNeededFiles();
        for (VirtualFile hisFile : hisFiles) {
          myFilesToCacheUpdaters.put(hisFile, updater);
        }
      }

      Set<VirtualFile> files = myFilesToCacheUpdaters.keySet();
      return files.toArray(new VirtualFile[files.size()]);
    }

    public void processFile(FileContent fileContent) {
      VirtualFile vFile = fileContent.getVirtualFile();
      CacheUpdater updater = myFilesToCacheUpdaters.get(vFile);
      LOG.assertTrue(updater != null);
      updater.processFile(fileContent);
    }

    public void updatingDone() {
      for (ScalaFilesCache cache : myModuleFilesCachesMap.values()) {
        cache.getCacheUpdater().updatingDone();
      }
    }

    public void canceled() {
    }
  }

}
