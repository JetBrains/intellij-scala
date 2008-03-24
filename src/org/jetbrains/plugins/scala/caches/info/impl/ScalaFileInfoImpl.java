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

import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.scala.caches.info.ScalaFileInfo;

/**
 * @author ilyas
 */
public class ScalaFileInfoImpl extends ScalaInfoBaseImpl implements ScalaFileInfo {
  private final String[] myClassNames;
  private String myPackageName;
  private String[] myNamesInExtends;

  ScalaFileInfoImpl(final String fileName,
                             final String url,
                             final long timestamp,
                             final String[] classNames, //files for directory or classes for file
                             String packageName,
                             String[] namesInExtends) {
    super(timestamp, fileName, url);
    myClassNames = classNames;
    myPackageName = packageName;
    myNamesInExtends = namesInExtends;
  }

  public boolean isDirectory() {
    return myPackageName == null;
  }

  public String[] getClassNames() {
    return myClassNames;
  }

  public String getParentUrl() {
    int i = myUrl.lastIndexOf('/');
    if (i <= 0) return null;

    String s = myUrl.substring(0, i);
    if (s.endsWith("!")) return null; //was jar separator
    return s;
  }


  public String[] getQualifiedClassNames() {
    if (isDirectory()) return ArrayUtil.EMPTY_STRING_ARRAY;

    if (myPackageName.length() == 0) return getClassNames();

    String[] result = new String[myClassNames.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = myPackageName + "." + myClassNames[i];
    }

    return result;
  }

  public String[] getExtendedNames() {
    return myNamesInExtends;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public String toString() {
    return myFileName + " [" + myTimestamp + "]";
  }

}
