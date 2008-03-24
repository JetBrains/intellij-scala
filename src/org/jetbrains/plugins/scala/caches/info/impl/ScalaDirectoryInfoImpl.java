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

import org.jetbrains.plugins.scala.caches.info.ScalaDirectoryInfo;

/**
 * @author ilyas
 */
public class ScalaDirectoryInfoImpl extends ScalaInfoBaseImpl implements ScalaDirectoryInfo {
  private String[] myChildrenNames;

  public ScalaDirectoryInfoImpl(final long timestamp, final String url,  final String fileName, final String[] childrenNames) {
    super(timestamp, url, fileName);
    myChildrenNames = childrenNames;
  }

  public String[] getChildrenNames() {
    return myChildrenNames;
  }
}
