/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category
import java.io.File

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

/**
 * @author ilyas
 */
@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaResolveTestCase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def folderPath: String = {
    TestUtils.getTestDataPath + "/"
  }
  
  protected def findReferenceAtCaret(): PsiReference = {
    getFileAdapter.findReferenceAt(getEditorAdapter.getCaretModel.getOffset);
  }

  override def setUp(): Unit = {
    super.setUp();

    var extention = ".scala";
    var fileName = getTestName(false);
    if (fileName.startsWith("JavaFileWithName")) {
      extention = ".java";
      fileName = fileName.substring("JavaFileWithName".length());
    }
    val filePath = folderPath + File.separator + fileName + extention;
    val ioFile = new File(filePath);
    val fileTextRaw = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8);
    val fileText = StringUtil.convertLineSeparators(fileTextRaw);
    val offset = fileText.indexOf("<ref>");
    val fileTextWithoutRef = fileText.replace("<ref>", "");
    configureFromFileTextAdapter(ioFile.getName, fileTextWithoutRef);
    if (offset != -1) {
      LightPlatformCodeInsightTestCase.getEditor.getCaretModel.moveToOffset(offset);
    }
  }
}
