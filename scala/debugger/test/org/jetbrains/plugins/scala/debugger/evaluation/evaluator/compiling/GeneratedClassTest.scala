package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator
package compiling

import com.intellij.openapi.util.io.FileUtil.loadFile
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, fail}
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[DebuggerTests]))
abstract class GeneratedClassTestBase extends ScalaLightCodeInsightFixtureTestCase {
  import GeneratedClassTestBase.TestData

  private val bp = "<breakpoint>"
  private val testDataSeparator = "------------"
  private val generatedClassNamePlaceholder = "<generated_class>"

  protected def testDataBasePath: String = TestUtils.getTestDataPath + "/debuggerTestData/evaluation/evaluator/generatedClass/"

  private def loadFileText(): String = {
    val path = testDataBasePath + getTestName(true) + ".test"
    convertLineSeparators(loadFile(new File(path), CharsetToolkit.UTF8))
  }

  private def parseTestData(): TestData =
    loadFileText().split(testDataSeparator) match {
      case Array(fileText, codeFragment, additionalImports, resultText) =>
        TestData(
          fileText = fileText.trim,
          codeFragment = codeFragment.trim,
          additionalImports = additionalImports.trim.split("\n").toSeq,
          resultText = resultText.trim
        )
      case array =>
        fail(s"Expected 4 sections: fileText, codeFragment, additionalImports, resultText; got: ${array.length}").asInstanceOf[Nothing]
    }

  protected def doTest(): Unit = {
    val testData = parseTestData()

    val file = configureFromFileText(testData.fileText.replace(bp, ""))
    val fragment = ScalaCodeFragment(testData.codeFragment)(file.getProject)
    fragment.addImportsFromString(testData.additionalImports.mkString(","))
    val context = PsiTreeUtilEx.topmostElementAtOffset(file, testData.fileText.indexOf(bp))

    val generatedClass = GeneratedClass(fragment, context)

    val expectedText = testData.resultText.replace(generatedClassNamePlaceholder, generatedClass.generatedClassName)
    val actualText = generatedClass.syntheticFile.getText

    checkTextHasNoErrors(actualText)
    assertEquals(expectedText, actualText)
  }
}

object GeneratedClassTestBase {
  final case class TestData(fileText: String, codeFragment: String, additionalImports: Seq[String], resultText: String)
}

class GeneratedClassTest_Scala2_2_11 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_2_11)

class GeneratedClassTest_Scala2_2_12 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_2_12)

class GeneratedClassTest_Scala2_2_13 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_2_13)

class GeneratedClassTest_Scala2_3_3 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_3)

class GeneratedClassTest_Scala2_3_4 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_4)

class GeneratedClassTest_Scala2_3_5 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_5)

class GeneratedClassTest_Scala2_3_6 extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_6)

class GeneratedClassTest_Scala2_3_LTS_RC extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_LTS_RC)

class GeneratedClassTest_Scala2_3_Next_RC extends GeneratedClassTest_Scala2_Base(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class GeneratedClassTest_Scala2_Base(scalaVersion: ScalaVersion) extends GeneratedClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  def testFromPattern(): Unit = doTest()

  def testInConstructor(): Unit = doTest()

  def testInForStmt(): Unit = doTest()

  def testInLambda(): Unit = doTest()

  def testSimplePlace(): Unit = doTest()
}

class GeneratedClassTest_Scala3_3_3 extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_3)

class GeneratedClassTest_Scala3_3_4 extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_4)

class GeneratedClassTest_Scala3_3_5 extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_5)

class GeneratedClassTest_Scala3_3_6 extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_6)

class GeneratedClassTest_Scala3_3_LTS_RC extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_LTS_RC)

class GeneratedClassTest_Scala3_3_Next_RC extends GeneratedClassTest_Scala3_Base(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class GeneratedClassTest_Scala3_Base(scalaVersion: ScalaVersion) extends GeneratedClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  override protected def testDataBasePath: String = super.testDataBasePath + "scala3/"

  def testFromPattern(): Unit = doTest()

  def testInConstructor(): Unit = doTest()

  def testInForStmt(): Unit = doTest()

  def testInForStmtBlock(): Unit = doTest()

  def testSimplePlace(): Unit = doTest()
}

private object PsiTreeUtilEx {
  def topmostElementAtOffset(file: PsiFile, offset: Int): PsiElement = {
    val element = file.findElementAt(offset)
    val elementStartOffset = element.startOffset

    element.withParents
      .takeWhile(_.startOffset == elementStartOffset)
      .reduceLeft((_, e) => e)
  }
}
