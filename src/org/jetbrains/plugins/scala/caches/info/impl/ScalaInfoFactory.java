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

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.caches.info.ScalaInfoBase;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.ArrayList;

public class ScalaInfoFactory {

  /**
   * Creates new ScalaFileInfo for given file
   *
   * @param project Current project
   * @param file    current file
   * @return ScalaFileInfo object containing information about file
   *         or null if file cannot be found or isn`t groovy file
   */

  @Nullable
  public static ScalaInfoBase createScalaFileInfo(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!file.isValid()) return null;

    if (file.isDirectory()) {
      ArrayList<String> childrenNames = new ArrayList<String>();
      for (VirtualFile child : file.getChildren()) {
        if (ScalaUtils.isScalaFileOrDirectory(file)) {
          childrenNames.add(child.getName());
        }
      }

      return createDirectoryInfo(
          file.getName(),
          file.getUrl(),
          file.getTimeStamp(),
          childrenNames.toArray(ArrayUtil.EMPTY_STRING_ARRAY));

    } else {

      Language language = ScalaFileType.SCALA_LANGUAGE;
      final FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
      final PsiManager manager = PsiManager.getInstance(project);
      FileViewProvider provider = factory != null ? factory.createFileViewProvider(file, language, manager, true) : null;
      PsiFile psiFile = provider != null ? provider.getPsi(StdLanguages.JAVA) : manager.findFile(file);

      ArrayList<String> classNames = new ArrayList<String>();
      ArrayList<String> namesInExtends = new ArrayList<String>();
      if (psiFile instanceof PsiJavaFile) {
        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        final String packageName = javaFile.getPackageName();
        for (PsiClass clazz : javaFile.getClasses()) {
          classNames.add(clazz.getName());
          for (PsiClass superClass : clazz.getSupers()) {
            namesInExtends.add(superClass.getName());
          }
        }
        return create(file.getName(),
            file.getUrl(),
            file.getTimeStamp(),
            packageName,
            classNames.toArray(new String[classNames.size()]),
            namesInExtends.toArray(new String[namesInExtends.size()]));
      }

      return null;
    }
  }

  public static ScalaDirectoryInfoImpl createDirectoryInfo(final String fileName,
                                                           final String url,
                                                           final long timestamp,
                                                           String[] childrenNames) {
    return new ScalaDirectoryInfoImpl(timestamp, url, fileName, childrenNames);
  }

  public static ScalaFileInfoImpl create(final String fileName,
                                         final String url,
                                         final long timestamp,
                                         String packageName, String[] classNames, String[] namesInExtends) {
    return new ScalaFileInfoImpl(fileName, url, timestamp, classNames, packageName, namesInExtends);
  }

}


