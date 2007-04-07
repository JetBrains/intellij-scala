/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.scala.cache.info.impl;

import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.io.Serializable;

/**
 * Stores info about all files, which contain classes with this name
 * 
 * @author Ilya.Sergey
 */
public class ShortNameInfo implements Serializable {
  private String className;
  private Set<String> fileUrls;

  public ShortNameInfo(String name, Collection<String> urls){
    setClassName(name);
    setFileUrls(new HashSet<String>());
    for (String url: urls){
      getFileUrls().add(url);
    }
  }

  public String getClassName() {
    return className;
  }

  private void setClassName(String className) {
    this.className = className;
  }

  public Set<String> getFileUrls() {
    return fileUrls;
  }

  private void setFileUrls(Set<String> fileUrls) {
    this.fileUrls = fileUrls;
  }
}
