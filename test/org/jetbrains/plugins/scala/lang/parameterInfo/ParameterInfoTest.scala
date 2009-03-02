package org.jetbrains.plugins.scala.lang.parameterInfo

import base.ScalaPsiTestCase
import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiManager, PsiElement}
import java.awt.Color
import java.lang.String
import com.intellij.vcsUtil.VcsUtil
import psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.03.2009
 */

class FunctionParameterInfoTest extends ScalaPsiTestCase {
  val caretMarker = "/*caret*/"

  /* //use it if you want to generate tests from appropriate folder
  def testGenerate {
    generateTests
  }
  */
  
  protected def getTestOutput(file: VirtualFile): String = {
    val res = new StringBuilder("")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(caretMarker)
    assert(offset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)
    val context = new ShowParameterInfoContext(editor, myProject, scalaFile, offset, -1)
    val handler = new ScalaFunctionParameterInfoHandler
    val leafElement = scalaFile.findElementAt(offset)
    val element = PsiTreeUtil.getParentOfType(leafElement, handler.getArgumentListClass)
    handler.findElementForParameterInfo(context)

    for (item <- context.getItemsToShow) {
      val uiContext = new ParameterInfoUIContext {
        def getDefaultParameterColor: Color = HintUtil.INFORMATION_COLOR
        def setupUIComponentPresentation(text: String, highlightStartOffset: Int, highlightEndOffset: Int,
                                        isDisabled: Boolean, strikeout: Boolean, isDisabledBeforeHighlight: Boolean,
                                        background: Color): Unit = {
          res.append(text).append("\n")
        }
        def isUIComponentEnabled: Boolean = false
        def getCurrentParameterIndex: Int = 0
        def getParameterOwner: PsiElement = element
        def setUIComponentEnabled(enabled: Boolean): Unit = {}
      }
      handler.updateUI(item, uiContext)
    }
    if (res.length > 0) res.replace(res.length - 1, res.length, "")
    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)
    res.toString
  }

  private def generateTests {
    import com.intellij.openapi.fileTypes.FileTypeManager
    import com.intellij.openapi.vcs.VcsBundle
    import com.intellij.openapi.vfs.LocalFileSystem
    import com.intellij.openapi.vfs.VirtualFile
    import com.intellij.vcsUtil.VcsUtil
    import java.io.File
    import com.intellij.vcsUtil.VcsUtil
    import org.jetbrains.plugins.scala.util.TestUtils

    val testDataPath = TestUtils.getTestDataPath
    val testPaths = testDataPath + "/parameterInfo"

    val files = new java.util.ArrayList[VirtualFile]()
    VcsUtil.collectFiles(LocalFileSystem.getInstance.findFileByPath(testPaths.replace(File.separatorChar, '/')), files, true, false)

    for (file: VirtualFile <- files.toArray(Array[VirtualFile]())) {
      print("  def test" + file.getNameWithoutExtension + "{\n")
      val path = file.getPath
      print("    testPath = \"" + path.substring(path.indexOf("parameterInfo") + 13, path.indexOf(".scala")) + "\"\n")
      print("    realOutput = \"\"\"\n")
      print(getTestOutput(file) + "\n\"\"\"\n")
      print("    realOutput = realOutput.trim\n")
      print("    playTest\n  }\n\n")
    }
  }
}