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

package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File
import scala.annotation.nowarn
import scala.collection.mutable
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}

/**
 * @author ilyas
 */
@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaResolveTestCase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def folderPath: String =
    TestUtils.getTestDataPath + "/"

  override protected def sharedProjectToken =
    if (sourceRootPath == null) SharedTestProjectToken(this.getClass)
    else SharedTestProjectToken.DoNotShare

  protected def findReferenceAtCaret(): PsiReference =
    getFileAdapter.findReferenceAt(getEditorAdapter.getCaretModel.getOffset)

  protected def findAllReferencesAtCarets: Seq[PsiReference] = {
    val carets = getEditorAdapter.getCaretModel.getAllCarets.asScala.toSeq
    assertTrue("no carets found", carets.nonEmpty)
    carets.map { caret =>
      val offset = caret.getOffset
      getFileAdapter.findReferenceAt(offset)
    }
  }

  override def setUp(): Unit = {
    super.setUp()

    var extention = ".scala"
    var fileName = getTestName(false)
    if (fileName.startsWith("JavaFileWithName")) {
      extention = ".java"
      fileName = fileName.substring("JavaFileWithName".length())
    }
    val filePath = folderPath + File.separator + fileName + extention
    val ioFile = new File(filePath)
    val fileTextRaw = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    val fileTextOriginal = StringUtil.convertLineSeparators(fileTextRaw)

    setupEditor(ioFile.getName, fileTextOriginal)
  }

  private val RefTag = "<ref>"

  private def setupEditor(fileName: String, fileTextWithRefs: String): String = {
    val caretOffsets = mutable.ArrayBuffer.empty[Int]

    var fileText = fileTextWithRefs
    var offset = fileText.indexOf(RefTag)
    while (offset != -1) {
      caretOffsets += offset

      fileText = fileText.substring(0, offset) + fileText.substring(offset + RefTag.length, fileText.length)
      offset = fileText.indexOf(RefTag)
    }

    configureFromFileTextAdapter(fileName, fileText)

    val caretModel = getEditor.getCaretModel
    caretOffsets.toSeq match {
      case Seq()       =>
      case Seq(offset) => caretModel.moveToOffset(offset)
      case offsets     =>
        val caretStates = offsets.map { o =>
          val position = getEditor.offsetToLogicalPosition(o)
          new CaretState(position, null, null)
        }
        caretModel.setCaretsAndSelections(caretStates.asJava)

        val carets = caretModel.getAllCarets.asScala
        assertTrue(carets.size == caretStates.length)
    }

    fileText
  }
}
