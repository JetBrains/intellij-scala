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

package org.jetbrains.plugins.scala.caches;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.caches.info.ScalaFileInfo;

import java.util.Collection;

/**
 * @author ilyas
 */

public interface ScalaFilesCache {

  public void init(String cacheName, String filePath);

  public void dispose();

  public void setCacheUrls(@NotNull String[] myCacheUrls);

  public Collection<ScalaFileInfo> getAllClasses();

  public void saveCacheToDisk();

  public PsiClass getClassByName(@NotNull final String name);

  @NotNull
  public PsiClass[] getClassesByName(@NotNull final String name);

  public Collection<String> getAllClassNames();

  public Collection<String> getAllFileNames();

  public Collection<String> getAllClassShortNames();

  public PsiClass[] getClassesByShortClassName(@NotNull String shortName);

  @NotNull
  Collection<PsiClass> getDeriverCandidatess(String name);

  void refresh();

  void processFileChanged(@NotNull VirtualFile file);

  void processFileDeleted(@NotNull String fileUrl);

  CacheUpdater getCacheUpdater();
}