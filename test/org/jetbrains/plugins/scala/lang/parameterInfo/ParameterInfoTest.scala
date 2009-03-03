package org.jetbrains.plugins.scala.lang.parameterInfo

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.runtime.RichString
import _root_.scala.util.Sorting
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

  /*//use it if you want to generate tests from appropriate folder
  def testGenerate {
    generateTests
  }*/

  def testClassApply{
    testPath = "/parameterInfo/apply/ClassApply"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testGenericClassApply{
    testPath = "/parameterInfo/apply/GenericClassApply"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testObjectApply{
    testPath = "/parameterInfo/apply/ObjectApply"
    realOutput = """
x: Double, y: Int
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testObjectGenericApply{
    testPath = "/parameterInfo/apply/ObjectGenericApply"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testClassGenericApply{
    testPath = "/parameterInfo/apply/ClassGenericApply"
    realOutput = """
x: Int
x: Double
"""
    realOutput = realOutput.trim
    playTest
  }

  def testSimple{
    testPath = "/parameterInfo/simple/Simple"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testLocal{
    testPath = "/parameterInfo/simple/Local"
    realOutput = """
x: Boolean
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testGenericJavaLibrary{
    testPath = "/parameterInfo/simple/GenericJavaLibrary"
    realOutput = """
index: Int, element: Int
o: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testJavaLibrary{
    testPath = "/parameterInfo/simple/JavaLibrary"
    realOutput = """
ch: Int
ch: Int, fromIndex: Int
source: Array[Char], sourceOffset: Int, sourceCount: Int, target: Array[Char], targetOffset: Int, targetCount: Int, fromIndex: Int
str: String
str: String, fromIndex: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testScalaLibrary{
    testPath = "/parameterInfo/simple/ScalaLibrary"
    realOutput = """
s: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testGenericUpdate{
    testPath = "/parameterInfo/update/GenericUpdate"
    realOutput = """
x: Boolean
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testNoUpdate{
    testPath = "/parameterInfo/update/NoUpdate"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testUpdateOnly{
    testPath = "/parameterInfo/update/UpdateOnly"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testFunctionType{
    testPath = "/parameterInfo/functionType/FunctionType"
    realOutput = """
p0: String
"""
    realOutput = realOutput.trim
    playTest
  }

  def testFunctionTypeTwo{
    testPath = "/parameterInfo/functionType/FunctionTypeTwo"
    realOutput = """
v1: T1, v2: T2
"""
    realOutput = realOutput.trim
    playTest
  }

  def testApplyCurrings{
    testPath = "/parameterInfo/currings/ApplyCurrings"
    realOutput = """
p0: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testCurringDef{
    testPath = "/parameterInfo/currings/CurringDef"
    realOutput = """
p0: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testFunctionTypeCurrings{
    testPath = "/parameterInfo/currings/FunctionTypeCurrings"
    realOutput = """
p0: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testNoCurrings{
    testPath = "/parameterInfo/currings/NoCurrings"
    realOutput = """

"""
    realOutput = realOutput.trim
    playTest
  }

  def testGenericScalaConstructor{
    testPath = "/parameterInfo/constructors/GenericScalaConstructor"
    realOutput = """
x: Boolean
"""
    realOutput = realOutput.trim
    playTest
  }

  def testJavaConstructor{
    testPath = "/parameterInfo/constructors/JavaConstructor"
    realOutput = """
<no parameters>
c: Collection[_ <: Int]
initialCapacity: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testScalaConstructor{
    testPath = "/parameterInfo/constructors/ScalaConstructor"
    realOutput = """
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  def testThisScalaConstructor{
    testPath = "/parameterInfo/constructors/ThisScalaConstructor"
    realOutput = """
<no parameters>
x: Boolean
x: Int
"""
    realOutput = realOutput.trim
    playTest
  }

  protected def getTestOutput(file: VirtualFile): String =  getTestOutput(file, true)
  private def getTestOutput(file: VirtualFile, useOutput: Boolean): String = {
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
    val items = new ArrayBuffer[String]

    for (item <- context.getItemsToShow) {
      val uiContext = new ParameterInfoUIContext {
        def getDefaultParameterColor: Color = HintUtil.INFORMATION_COLOR
        def setupUIComponentPresentation(text: String, highlightStartOffset: Int, highlightEndOffset: Int,
                                        isDisabled: Boolean, strikeout: Boolean, isDisabledBeforeHighlight: Boolean,
                                        background: Color): Unit = {
          items.append(text)
        }
        def isUIComponentEnabled: Boolean = false
        def getCurrentParameterIndex: Int = 0
        def getParameterOwner: PsiElement = element
        def setUIComponentEnabled(enabled: Boolean): Unit = {}
      }
      handler.updateUI(item, uiContext)
    }

    val itemsArray = items.toArray
    Sorting.quickSort[String](itemsArray)

    val res = new StringBuilder("")

    for (item <- itemsArray) res.append(item).append("\n")
    if (res.length > 0) res.replace(res.length - 1, res.length, "")
    if (useOutput) {
      println("------------------------ " + scalaFile.getName + " ------------------------")
      println(res)
    }
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

    for (file: VirtualFile <- files.toArray(Array[VirtualFile]()) if file.getExtension == "scala") {
      print("  def test" + file.getNameWithoutExtension + "{\n")
      val path = file.getPath
      print("    testPath = \"/" + path.substring(path.indexOf("parameterInfo"), path.indexOf(".scala")) + "\"\n")
      print("    realOutput = \"\"\"\n")
      print(getTestOutput(file, false) + "\n\"\"\"\n")
      print("    realOutput = realOutput.trim\n")
      print("    playTest\n  }\n\n")
    }
  }
}