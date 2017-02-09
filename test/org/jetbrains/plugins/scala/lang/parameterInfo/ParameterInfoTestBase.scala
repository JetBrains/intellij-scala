package org.jetbrains.plugins.scala
package lang
package parameterInfo

import java.awt.Color
import java.io.File

import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo.{ParameterInfoHandlerWithTabActionSupport, ParameterInfoUIContext}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.junit.Assert._

import scala.collection.mutable

abstract class ParameterInfoTestBase[O <: PsiElement, E <: ScalaPsiElement] extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  protected def folderPath = baseRootPath() + "parameterInfo/"

  protected def createHandler: ParameterInfoHandlerWithTabActionSupport[O, Any, E]

  import ParameterInfoTestBase._

  protected final def doTest(): Unit = {
    val filename = getTestName(false) + ".scala"

    val filePath = folderPath + filename
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"File $filePath not found.", file)

    val fileText = FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8)
    configureFromFileTextAdapter(filename, fileText)

    val offset = fileText.indexOf(CARET_MARKER)
    assertNotEquals("Caret not found", offset, -1)

    val context = createInfoContext(file, offset)
    val actual = handleUI(findElementAt(offset), context)

    val expected = expectedSignatures(findElementAt())
    assertTrue(expected.contains(actual))
  }

  private def handleUI(element: PsiElement,
                       context: ShowParameterInfoContext): Seq[String] = {
    val handler = createHandler
    handler.findElementForParameterInfo(context)

    val items = mutable.SortedSet.empty[String]
    val parameterOwner = getParentOfType(element, handler.getArgumentListClass)

    context.getItemsToShow.foreach { item =>
      val uiContext = createInfoUIContext(parameterOwner) {
        items += _
      }
      handler.updateUI(item, uiContext)
    }

    items.toSeq.flatMap(normalize)
  }

  private def createInfoContext(file: VirtualFile, offset: Int) = {
    val project = getProjectAdapter
    val descriptor = new OpenFileDescriptor(project, file, offset)
    val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, false)
    new ShowParameterInfoContext(editor, project, getFileAdapter, offset, -1)
  }

  private def findElementAt(offset: Int = -1) = {
    val file = getFileAdapter
    val newOffset = offset match {
      case -1 => file.getTextLength - 1
      case _ => offset
    }
    file.findElementAt(newOffset)
  }
}

object ParameterInfoTestBase {
  private val CARET_MARKER = "/*caret*/"

  private def createInfoUIContext(parameterOwner: PsiElement)
                                 (consume: String => Unit) = new ParameterInfoUIContext {
    def getParameterOwner: PsiElement = parameterOwner

    def setupUIComponentPresentation(text: String, highlightStartOffset: Int, highlightEndOffset: Int,
                                     isDisabled: Boolean, strikeout: Boolean, isDisabledBeforeHighlight: Boolean,
                                     background: Color): String = {
      consume(text)
      text
    }

    def getDefaultParameterColor: Color = HintUtil.INFORMATION_COLOR

    def isUIComponentEnabled: Boolean = false

    def getCurrentParameterIndex: Int = 0

    def setUIComponentEnabled(enabled: Boolean): Unit = {}
  }

  private def expectedSignatures(lastElement: PsiElement): Seq[Seq[String]] = {
    val dropRight = lastElement.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => 0
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT => 2
    }

    val text = lastElement.getText
    text.substring(2, text.length - dropRight)
      .split("<--->")
      .map(normalize)
  }

  private def normalize(string: String) =
    string.split(System.lineSeparator)
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSeq
}

