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

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;

/**
 * @author ilyas
 */
public class ScalaFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {

  public static final ScalaFileType SCALA_FILE_TYPE = new ScalaFileType();
  public static final Language SCALA_LANGUAGE = ScalaLanguage.Instance;
  @NonNls public static final String DEFAULT_EXTENSION = "scala";
  @NonNls public static final String WORKSHEET_EXTENSION = "sc";

  private ScalaFileType() {
    super(ScalaLanguage.Instance);
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
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
     return Icons.FILE_TYPE_LOGO;
  }

  // TODO Temporary fix for Upsource
  // (FileTypeManagerImpl.isFileOfType is called from ScalaSourceFilterScope,
  // while FileTypeManager contains no .scala pattern)
  @Override
  public boolean isMyFileType(@NotNull VirtualFile virtualFile) {
    return virtualFile.getName().endsWith(".scala");
  }
}
