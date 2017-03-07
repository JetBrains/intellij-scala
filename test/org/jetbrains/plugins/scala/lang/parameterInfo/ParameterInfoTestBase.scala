package org.jetbrains.plugins.scala
package lang
package parameterInfo

import java.awt.Color
import java.io.File

import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo._
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiElement
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.junit.Assert._

import scala.collection.mutable

abstract class ParameterInfoTestBase[Owner <: PsiElement] extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  protected def folderPath = baseRootPath() + "parameterInfo/"

  protected def createHandler: ParameterInfoHandlerWithTabActionSupport[Owner, Any, _ <: PsiElement]

  import ParameterInfoTestBase._

  protected final def doTest(): Unit = {
    val filename = getTestName(false) + ".scala"

    val filePath = folderPath + filename
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"File $filePath not found.", file)

    val fileText = FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8)
    configureFromFileTextAdapter(filename, fileText)

    val offset = fileText.indexOf(CARET_MARKER)
    getEditorAdapter.getCaretModel.moveToOffset(offset)
    assertNotEquals("Caret not found", offset, -1)
    val handler = createHandler

    val createCtx = createContext(file, offset)
    val actual = handleUI(handler, createCtx)

    val expected = expectedSignatures(findElementAt())
    assertTrue(expected.contains(actual))

    //todo test correct parameter index after moving caret
    val afterUpdate = handleUpdateUI(handler, createCtx)
    assertTrue(expected.contains(afterUpdate))
  }

  private def handleUI(handler: ParameterInfoHandler[Owner, Any],
                       context: CreateParameterInfoContext): Seq[String] = {

    val parameterOwner = handler.findElementForParameterInfo(context)
    uiStrings(context.getItemsToShow, handler, parameterOwner)
  }

  private def handleUpdateUI(handler: ParameterInfoHandler[Owner, Any], createCtx: CreateParameterInfoContext): Seq[String] = {
    val items = createCtx.getItemsToShow
    val updateCtx = updateContext(items)
    val parameterOwner = handler.findElementForUpdatingParameterInfo(updateCtx)
    updateCtx.setParameterOwner(parameterOwner)
    handler.updateParameterInfo(parameterOwner, updateCtx)
    uiStrings(updateCtx.getObjectsToView, handler, parameterOwner)
  }

  private def uiStrings(items: Seq[AnyRef], handler: ParameterInfoHandler[Owner, Any], parameterOwner: Owner): Seq[String] = {
    val result = mutable.SortedSet.empty[String]
      items.foreach { item =>
        val uiContext = createInfoUIContext(parameterOwner) {
          result += _
        }
        handler.updateUI(item, uiContext)
      }

    result.toSeq.flatMap(normalize)
  }

  private def createContext(file: VirtualFile, offset: Int): CreateParameterInfoContext = {
    val project = getProjectAdapter
    val descriptor = new OpenFileDescriptor(project, file, offset)
    val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, false)
    new ShowParameterInfoContext(editor, project, getFileAdapter, offset, -1)
  }

  private def updateContext(itemsToShow: Array[AnyRef]): UpdateParameterInfoContext = {
    new MockUpdateParameterInfoContext(getEditorAdapter, getFileAdapter, itemsToShow) {
      private var items: Array[AnyRef] = itemsToShow

      override def getObjectsToView: Array[AnyRef] = items

      override def removeHint(): Unit = {
        items = Array.empty
      }
    }
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
    StringUtil.convertLineSeparators(string)
      .split('\n')
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSeq
}

