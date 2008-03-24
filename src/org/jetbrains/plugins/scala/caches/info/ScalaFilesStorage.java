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

package org.jetbrains.plugins.scala.caches.info;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author ilyas
 */
public interface ScalaFilesStorage {

  /**
   * Returns file info by url
   *
   * @param fileUrl
   * @return
   */
  public ScalaInfoBase getScalaFileInfoByFileUrl(@NotNull final String fileUrl);

  /**
   * Adds new Scala file info into storage
   *
   * @param gInfo
   */
  public void addScalaInfo(@NotNull final ScalaInfoBase gInfo);

  /**
   * Removes Scala info from storage
   *
   * @param recursively
   * @param fileUrl
   * @return @param fileUrl
   */
  public ScalaInfoBase removeScalaInfo(@NotNull final String fileUrl, boolean recursively);

  public Collection<ScalaFileInfo> getAllScalaFileInfos();

  public Collection<ScalaInfoBase> getAllInfos();

  public Collection<String> getAllClassNames();

  public Collection<String> getAllClassShortNames();

  public Collection<String> getFileUrlsByClassName(@NotNull final String name);

  public Iterable<String> getFileUrlsByShortClassName(@NotNull final String name);

  @NotNull
  public Collection<String> getDerivedFileUrlsByShortClassName(@NotNull final String name);
}