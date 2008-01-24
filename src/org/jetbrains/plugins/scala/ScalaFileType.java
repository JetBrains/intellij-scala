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

package org.jetbrains.plugins.scala;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:25:12
 */
public class ScalaFileType extends LanguageFileType {

  public static final ScalaFileType SCALA_FILE_TYPE = new ScalaFileType();
  public static final Icon SCALA_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_logo.png");

  private ScalaFileType() {
    super(new ScalaLanguage());
  }

  @NotNull
  @NonNls
  public String getName() {
    return "Scala";
  }

  @NotNull
  public String getDescription() {
    return "Scala files";
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "scala";
  }

  public Icon getIcon() {
    return SCALA_LOGO;
  }

  public boolean isJVMDebuggingSupported() {
    return true;
  }
}
