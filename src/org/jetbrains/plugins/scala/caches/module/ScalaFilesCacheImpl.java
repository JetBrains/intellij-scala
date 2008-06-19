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

package org.jetbrains.plugins.scala.caches.module;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;
import org.jetbrains.plugins.scala.caches.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.caches.info.ScalaFilesStorage;
import org.jetbrains.plugins.scala.caches.info.ScalaInfoBase;
import org.jetbrains.plugins.scala.caches.info.impl.IOUtil;
import org.jetbrains.plugins.scala.caches.info.impl.NameInfo;
import org.jetbrains.plugins.scala.caches.info.impl.ScalaFilesStorageImpl;
import org.jetbrains.plugins.scala.caches.info.impl.ScalaInfoFactory;
import org.jetbrains.plugins.scala.caches.listeners.ScalaPsiTreeListener;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.io.*;
import java.util.*;

/**
 * @author ilyas
 *
 */
public class ScalaFilesCacheImpl implements ScalaFilesCache {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.caches.module.ScalaFilesCacheImpl");

  protected final Project myProject;
  private String[] myCacheUrls;
  private String myCacheName;
  private String myCacheDataFilePath;
  private ScalaFilesStorage myScalaFilesStorage;
  private MyCacheUpdater myCacheUpdater;
  private static final int VERSION = 2;

  private boolean myFlushToDiskPending = false;

  private Set<String> myOutOfDateFileUrls = new HashSet<String>();
  private Module myModule;


  public ScalaFilesCacheImpl(final Module module) {
    myProject = module.getProject();
    myModule = module;
  }

  private void setCacheName(@NotNull String cacheName) {
    myCacheName = cacheName;
  }

  public void setCacheUrls(@NotNull String[] cacheUrls) {
    myCacheUrls = cacheUrls;
  }

  private void setCacheFilePath(@NotNull final String dataFileUrl) {
    myCacheDataFilePath = dataFileUrl;
  }

  /**
   * Initialisation of cache
   *
   * @param cacheName
   * @param filePath
   */
  public void init(String cacheName, String filePath) {
    setCacheName(cacheName);
    setCacheFilePath(filePath);
    registerListeners();
  }

  public CacheUpdater getCacheUpdater() {
    if (myCacheUpdater == null) {
      myCacheUpdater = new MyCacheUpdater();
    }

    return myCacheUpdater;
  }

  public void dispose() {
    removeCacheFile();
    unregisterListeners();
  }

  public String getCacheName() {
    return myCacheName;
  }

  /**
   * Returns information about all files in module
   *
   * @return
   */
  public Collection<ScalaFileInfo> getAllClasses() {
    refresh();
    return myScalaFilesStorage.getAllScalaFileInfos();
  }


  private void removeScalaFileInfos(@NotNull Set<String> urlsToDelete) {
    for (String url : urlsToDelete) {
      myScalaFilesStorage.removeScalaInfo(url, true);
    }
  }


  /**
   * @param file        File for what restoring cached information
   * @param forceUpdate Force update or not
   * @return Old info about file if not changed or new whil regenerateFileInfo(file)
   */
  protected synchronized ScalaInfoBase getUp2DateFileInfo(VirtualFile file, final boolean forceUpdate) {
    final ScalaInfoBase fileInfo = myScalaFilesStorage.getScalaFileInfoByFileUrl(file.getUrl());
    if (fileInfo != null && fileInfo.getFileTimestamp() == file.getTimeStamp()) {
      return fileInfo;
    }
    if (!forceUpdate && fileInfo != null) {
      return fileInfo;
    } else {
      return regenerateFileInfo(file, true);
    }
  }

  /**
   * Generates cabout file
   *
   * @param file
   * @param recursively
   * @return final boolean forceUpdate
   */
  protected ScalaInfoBase regenerateFileInfo(@NotNull VirtualFile file, boolean recursively) {
    final String url = file.getUrl();
    removeScalaFileInfo(url, recursively);
    return createScalaFileInfo(file);
  }


  /**
   * Removes ScalaFileInfo
   *
   * @param fileUrl     url for file
   * @param recursively
   */
  protected void removeScalaFileInfo(@Nullable final String fileUrl, boolean recursively) {
    if (fileUrl != null) {
      myScalaFilesStorage.removeScalaInfo(fileUrl, recursively);
    }
  }

  /**
   * Creates ScalaFileInfo for file according corresponding ScalaFile
   *
   * @param file VirtualFile
   * @return new ScalaFileInfo for file or null.
   */
  public synchronized ScalaInfoBase createScalaFileInfo(@NotNull final VirtualFile file) {
    final ScalaInfoBase fileInfo = ScalaInfoFactory.createScalaFileInfo(myProject, file);

    if (fileInfo == null) {
      return null;
    }
    myScalaFilesStorage.addScalaInfo(fileInfo);
    return fileInfo;
  }


  /**
   * Scanning for new files
   *
   * @param urls
   * @return
   */
  private Set<VirtualFile> scanForFiles(@NotNull final String[] urls) {
    VirtualFileManager fManager = VirtualFileManager.getInstance();
    final Set<VirtualFile> filesToAdd = new HashSet<VirtualFile>();
    for (String url : urls) {
      final VirtualFile root = fManager.findFileByUrl(url);
      final ModuleFileIndex index = ModuleRootManager.getInstance(myModule).getFileIndex();
      index.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile virtualFile) {
          if (!virtualFile.isDirectory() &&
              FileTypeManager.getInstance().getFileTypeByFile(virtualFile) == ScalaFileType.SCALA_FILE_TYPE &&
              (index.isInSourceContent(virtualFile) || index.isInTestSourceContent(virtualFile))) {
            filesToAdd.add(virtualFile);
          }
          return true;
        }
      });
    }
    return filesToAdd;
  }


  /**
   * Returns instance of PsiClass by qualified qualifiedClassName
   *
   * @param qualifiedClassName - Qualified class qualifiedClassName
   * @return
   */
  public PsiClass getClassByName(@NotNull final String qualifiedClassName) {
    refresh();
    String name = getShortClassName(qualifiedClassName);
    if (myScalaFilesStorage == null) return null;
    Collection<String> urls = myScalaFilesStorage.getFileUrlsByClassName(qualifiedClassName);
    for (String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      final PsiManager myPsiManager = PsiManager.getInstance(myProject);
      if (file != null) {
        PsiFile psiFile = myPsiManager.findFile(file);
        if (psiFile instanceof ScalaFile) {
          ScalaFile scalaFile = (ScalaFile) psiFile;
          ScTypeDefinition[] typeDefs = scalaFile.getTypeDefinitionsArray();
          for (ScTypeDefinition typeDef : typeDefs) {
            if (typeDef.getName().equals(name)) return typeDef;
          }
        }
      }
    }

    return null;
  }

  private static String getShortClassName(String qualifiedClassName) {
    int dotIndex = qualifiedClassName.lastIndexOf(".");
    return dotIndex > 0 ? qualifiedClassName.substring(dotIndex + 1, qualifiedClassName.length()) : qualifiedClassName;
  }

  @NotNull
  public Collection<PsiClass> getDeriverCandidatess(String name) {
    refresh();
    final Collection<String> urls = myScalaFilesStorage.getDerivedFileUrlsByShortClassName(name);
    if (urls.isEmpty()) return Collections.emptyList();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (String url : urls) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(url);
      final PsiManager myPsiManager = PsiManager.getInstance(myProject);
      if (virtualFile != null) {
        PsiFile file = myPsiManager.findFile(virtualFile);
        if (file instanceof ScalaFile) {

          NextClass:
          for (PsiClass aClass : ((ScalaFile) file).getClasses()) {
            for (PsiClassType type : aClass.getSuperTypes()) {
              if (name.equals(type.getClassName())) {
                result.add(aClass);
                continue NextClass;
              }
            }
          }
        }
      }
    }

    return result;
  }

  private PsiFile getJavaFile(VirtualFile virtualFile) {
    Language language = ScalaFileType.SCALA_LANGUAGE;
    final FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
    final PsiManager manager = PsiManager.getInstance(myProject);
    FileViewProvider provider = factory != null ? factory.createFileViewProvider(virtualFile, language, manager, true) : null;
    return provider != null ? provider.getPsi(StdLanguages.JAVA) : manager.findFile(virtualFile);
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull final String qualifiedClassName) {
    String name = getShortClassName(qualifiedClassName);
    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    Collection<String> urls = myScalaFilesStorage.getFileUrlsByClassName(qualifiedClassName);
    for (String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      final PsiManager myPsiManager = PsiManager.getInstance(myProject);
      if (file != null) {
        PsiFile psiFile = myPsiManager.findFile(file);
        if (psiFile instanceof ScalaFile) {
          ScalaFile scalaFile = (ScalaFile) psiFile;
          ScTypeDefinition[] typeDefs = scalaFile.getTypeDefinitionsArray();
          for (ScTypeDefinition typeDef : typeDefs) {
            if (typeDef.getName().equals(name)) acc.add(typeDef);
          }
        }
      }
    }

    return acc.toArray(PsiClass.EMPTY_ARRAY);
  }

  /**
   * @return qualified names of all classes in current cache
   */
  public Collection<String> getAllClassNames() {
    refresh();
    return myScalaFilesStorage.getAllClassNames();
  }

  public Collection<String> getAllFileNames() {
    refresh();
    ArrayList<String> acc = new ArrayList<String>();
    for (ScalaFileInfo info : myScalaFilesStorage.getAllScalaFileInfos()) {
      acc.add(info.getFileName());
    }
    return acc;
  }

  /**
   * @return short names of all classes in current cache
   */
  public Collection<String> getAllClassShortNames() {
    refresh();
    return myScalaFilesStorage.getAllClassShortNames();
  }

  /**
   * Return all classes by short name
   *
   * @param shortName
   * @return
   */
  public PsiClass[] getClassesByShortClassName(@NotNull String shortName) {
    refresh();

    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    for (String url : myScalaFilesStorage.getFileUrlsByShortClassName(shortName)) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      final PsiManager myPsiManager = PsiManager.getInstance(myProject);
      if (file != null) {
        PsiFile psiFile = myPsiManager.findFile(file);
        if (psiFile instanceof ScalaFile) {
          ScalaFile scalaFile = (ScalaFile) psiFile;
          ScTypeDefinition[] typeDefinitions = scalaFile.getTypeDefinitionsArray();
          for (ScTypeDefinition typeDefinition : typeDefinitions) {
            if (shortName.equals(typeDefinition.getName())) {
              acc.add(typeDefinition);
            }
          }
        }
      }
    }

    return acc.toArray(PsiClass.EMPTY_ARRAY);
  }


  public void removeCacheFile() {
    final File dataFile = new File(myCacheDataFilePath);
    if (dataFile.exists()) {
      FileUtil.asyncDelete(dataFile);
    }
  }

  /**
   * Saves serialized cache data to dataFile
   */
  public void saveCacheToDisk() {
    if (myCacheDataFilePath != null) {
      final File dataFile = new File(myCacheDataFilePath);
      try {
        final File parentFile = dataFile.getParentFile();
        if (!parentFile.exists()) parentFile.mkdirs();
        if (!dataFile.exists()) dataFile.createNewFile();
        final DataOutputStream dataStream = new DataOutputStream(new FileOutputStream(dataFile));
        dataStream.writeInt(VERSION);
        final ScalaFilesStorageImpl storage = (ScalaFilesStorageImpl) myScalaFilesStorage;
        if (storage == null) return;
        final Map<String, ScalaInfoBase> path2FileInfo = storage.getPath2FileInfo();
        dataStream.writeInt(path2FileInfo.size());
        for (Map.Entry<String, ScalaInfoBase> entry : path2FileInfo.entrySet()) {
          dataStream.writeUTF(entry.getKey());
          IOUtil.writeFileInfo(entry.getValue(), dataStream);
        }

        final Map<String, NameInfo> class2FileInfo = storage.getClass2FileInfo();
        dataStream.writeInt(class2FileInfo.size());
        for (Map.Entry<String, NameInfo> entry : class2FileInfo.entrySet()) {
          dataStream.writeUTF(entry.getKey());
          IOUtil.writeNameInfo(entry.getValue(), dataStream);
        }

        final Map<String, NameInfo> shortclass2FileInfo = storage.getShortClass2FileInfo();
        dataStream.writeInt(shortclass2FileInfo.size());
        for (Map.Entry<String, NameInfo> entry : shortclass2FileInfo.entrySet()) {
          dataStream.writeUTF(entry.getKey());
          IOUtil.writeNameInfo(entry.getValue(), dataStream);
        }

        final Map<String, NameInfo> className2ExtendsOccurrences = storage.getClassName2ExtendsOccurrences();
        dataStream.writeInt(className2ExtendsOccurrences.size());
        for (Map.Entry<String, NameInfo> entry : className2ExtendsOccurrences.entrySet()) {
          dataStream.writeUTF(entry.getKey());
          IOUtil.writeNameInfo(entry.getValue(), dataStream);
        }
      } catch (FileNotFoundException e) {
        LOG.error(e);
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  /**
   * Tries to load cache data from disk!
   *
   * @return RFilesStorage object - if something loaded, null otherwise
   */
  @Nullable
  private ScalaFilesStorage loadCacheFromDisk() {
    final File dataFile = new File(myCacheDataFilePath);
    if (!dataFile.exists()) return null;
    try {
      final DataInputStream dataStream = new DataInputStream(new FileInputStream(dataFile));
      final int version = dataStream.readInt();
      if (version != VERSION) return null;
      final int path2FileInfoLength = dataStream.readInt();
      Map<String, ScalaInfoBase> path2FileInfo = new com.intellij.util.containers.HashMap<String, ScalaInfoBase>();
      for (int i = 0; i < path2FileInfoLength; i++) {
        final String name = dataStream.readUTF();
        path2FileInfo.put(name, IOUtil.readFileInfo(dataStream));
      }

      final int class2FileInfoLength = dataStream.readInt();
      Map<String, NameInfo> qname2FileInfo = new com.intellij.util.containers.HashMap<String, NameInfo>();
      for (int i = 0; i < class2FileInfoLength; i++) {
        final String name = dataStream.readUTF();
        qname2FileInfo.put(name, IOUtil.readNameInfo(dataStream));
      }

      final int shortClass2FileInfoLength = dataStream.readInt();
      Map<String, NameInfo> shortClass2FileInfo = new com.intellij.util.containers.HashMap<String, NameInfo>();
      for (int i = 0; i < shortClass2FileInfoLength; i++) {
        final String name = dataStream.readUTF();
        shortClass2FileInfo.put(name, IOUtil.readNameInfo(dataStream));
      }

      final int className2ExtendsOccLength = dataStream.readInt();
      Map<String, NameInfo> classNameToExtendsOccurrences = new com.intellij.util.containers.HashMap<String, NameInfo>();
      for (int i = 0; i < className2ExtendsOccLength; i++) {
        final String name = dataStream.readUTF();
        classNameToExtendsOccurrences.put(name, IOUtil.readNameInfo(dataStream));
      }

      return new ScalaFilesStorageImpl(path2FileInfo, qname2FileInfo, shortClass2FileInfo, classNameToExtendsOccurrences);
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Refreshes cache and may create new instanse if is not created yet
   *
   * @return
   */
  private Set<VirtualFile> getFilesToAdd() {
    final Set<VirtualFile> filesToAdd = scanForFiles(myCacheUrls);
    /**
     * Remove garbage
     */
    final VirtualFileManager fileManager = VirtualFileManager.getInstance();
    final Set<String> urls2Delete = new HashSet<String>();
    for (ScalaInfoBase fileInfo : myScalaFilesStorage.getAllInfos()) {
      final VirtualFile virtualFile = fileManager.findFileByUrl(fileInfo.getFileUrl());
      if (virtualFile == null || !filesToAdd.contains(virtualFile)) {
        urls2Delete.add(fileInfo.getFileUrl());
      }
    }
    removeScalaFileInfos(urls2Delete);
    myFlushToDiskPending = !urls2Delete.isEmpty() || !filesToAdd.isEmpty();
    return filesToAdd;
  }

  class MyCacheUpdater implements CacheUpdater {

    public VirtualFile[] queryNeededFiles() {
      myScalaFilesStorage = loadCacheFromDisk();

      if (myScalaFilesStorage == null) {
        myScalaFilesStorage = new ScalaFilesStorageImpl();
      }
      // Getting files to update list
      Set<VirtualFile> files = getFilesToAdd();
      return files.toArray(new VirtualFile[files.size()]);

    }

    public void processFile(FileContent fileContent) {
      getUp2DateFileInfo(fileContent.getVirtualFile(), true);
    }

    public void updatingDone() {
      // Save updated cache on disk
      if (myFlushToDiskPending) {
        saveCacheToDisk();
        myFlushToDiskPending = false;
      }
    }

    public void canceled() {
    }

  }

  protected boolean isInCache(String url) {
    for (String cacheUrl : myCacheUrls) {
      if (url.startsWith(cacheUrl)) return true;
    }

    return false;
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
    removeScalaFileInfo(url, true);
  }


  public synchronized void processFileChanged(final @NotNull VirtualFile file) {
    if (ScalaUtils.isScalaFileOrDirectory(file)) {
      myOutOfDateFileUrls.add(file.getUrl());
    }
  }

  public synchronized void refresh() {
    for (Iterator<String> iterator = myOutOfDateFileUrls.iterator(); iterator.hasNext();) {
      String url = iterator.next();
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      iterator.remove();
      if (file != null && isInCache(file.getUrl())) {
        regenerateFileInfo(file, false);
      }
    }
  }
}
