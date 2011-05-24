package org.jetbrains.plugins.scala.lang.completion2.smart

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import org.jetbrains.plugins.scala.ScalaFileType
import java.io.{FileReader, File}
import java.util.ArrayList
import collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

class SmartCompletionTestBase extends ScalaFixtureTestCase {
  private val caretMarker = "/*caret*/"

  override def rootPath: String = super.rootPath + "completion2/smart/"

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val testName = getTestName(false)
    val filePath = rootPath + testName + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    import scala.collection.JavaConversions._
    LocalFileSystem.getInstance().refreshFiles(asIterable(Seq(file).toIterable))
    assert(file != null, "file " + filePath + " not found")
    val fileText: String = new String(file.contentsToByteArray(false))
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, fileText)
    val scalaFile: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val offset = scalaFile.getText().indexOf(caretMarker)
    assert(offset != -1, "Not specified end marker in test case. Use /*caret*/ in scala file for this.")
    myFixture.getEditor.getCaretModel.moveToOffset(offset)

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    CodeInsightSettings.getInstance.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true
    myFixture.complete(CompletionType.SMART)
    val res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim

    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res.trim)
  }
}