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

package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ResolveTestCase;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class ScalaResolveTestCase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  public String folderPath() {
    return TestUtils.getTestDataPath() + "/";
  }
  
  protected PsiReference findReferenceAtCaret() {
    return getFileAdapter().findReferenceAt(getEditorAdapter().getCaretModel().getOffset());
  }

  protected void setUp() throws Exception {
    super.setUp();
    final SyntheticClasses syntheticClasses = getProjectAdapter().getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }

    String extention = ".scala";
    String fileName = getTestName(false);
    if (fileName.startsWith("JavaFileWithName")) {
      extention = ".java";
      fileName = fileName.substring("JavaFileWithName".length());
    }
    String filePath = folderPath() + File.separator + fileName + extention;
    File ioFile = new File(filePath);
    String fileText = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8);
    fileText = StringUtil.convertLineSeparators(fileText);
    int offset = fileText.indexOf("<ref>");
    fileText = fileText.replace("<ref>", "");
    configureFromFileTextAdapter(ioFile.getName(), fileText);
    if (offset != -1) {
      getEditor().getCaretModel().moveToOffset(offset);
    }
  }
}
