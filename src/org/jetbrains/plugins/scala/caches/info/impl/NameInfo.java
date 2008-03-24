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

package org.jetbrains.plugins.scala.caches.info.impl;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores info about all files, which contain classes with this name
 *
 * @author ilyas
 */
public class NameInfo {
  private String myClassName;
  private Set<String> myFileUrls;

  public NameInfo(String name) {
    this(name, new HashSet<String>());
  }
  public NameInfo(String name, Set<String> fileUrls) {
    myClassName = name;
    myFileUrls = fileUrls;
  }

  public String getClassName() {
    return myClassName;
  }

  public Set<String> getFileUrls() {
    return myFileUrls;
  }

  public void addFileUrl(String url) { myFileUrls.add(url); }

  public void removeFileUrl(String url) { myFileUrls.remove(url); }

  public boolean isEmpty() { return myFileUrls.isEmpty(); }
}