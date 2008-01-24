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

package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.VirtualFileScanner;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.io.fs.FileSystem;

import java.util.ArrayList;

/**
 * @author Ilya.Sergey
 */

public class ScalaFileInfoImpl implements ScalaFileInfo {

  private final String myFileName;
  private final String myFileDirectoryUrl;
  private final String myUrl;
  private final long myTimestamp;
  private transient PsiClass[] myClasses;
  private String[] myClassNames;

  public ScalaFileInfoImpl(final String fileName, final String url, final String directoryUrl, final long timestamp) {
    myFileName = fileName;
    myFileDirectoryUrl = directoryUrl;
    myTimestamp = timestamp;
    myUrl = url;
  }

  public long getFileTimestamp() {
    return myTimestamp;
  }

  public String getFileName() {
    return myFileName;
  }

  @NotNull
  public String getFileUrl() {
    return myUrl;
  }

  public String getFileDirectoryUrl() {
    return myFileDirectoryUrl;
  }


  private void setClassNames() {
    String classNames[] = new String[myClasses.length];
    for (int i = 0; i < myClasses.length; i++) {
      classNames[i] = myClasses[i].getQualifiedName();
    }
    myClassNames = classNames;
  }


  public boolean containsClass(String name) {
    for (String _name: myClassNames){
      if (_name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  public String[] getClassNames() {
    return myClassNames;
  }

  public void setClasses(PsiClass[] classes) {
    myClasses = classes;
    setClassNames();
  }

  public String toString() {
    return myFileName + " [" + myTimestamp + "]";
  }
}
