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

package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.info.ScalaFilesStorage;

import java.util.Collection;

import com.intellij.psi.PsiClass;

/**
 * @author Ilya.Sergey
 */
public interface ScalaFilesCache {

  public void init (boolean  b);

  public void dispose ();

  public void setCacheUrls(@NotNull String[] myCacheUrls);

  public void setCacheName(@NotNull String myCacheName);

  public Collection<ScalaFileInfo> getAllClasses();

  public void setCacheFilePath(@NotNull final String dataFileUrl);

  public void saveCacheToDisk(final boolean runProcessWithProgressSynchronously);

  public PsiClass getClassByName(@NotNull final String name);

  public PsiClass[] getClassesByName(@NotNull final String name);

  public Collection<String> getAllClassNames();

  public Collection<String> getAllClassShortNames();

  public PsiClass[] getClassesByShortClassName(@NotNull String shortName);

  public void removeCacheFile();

}
