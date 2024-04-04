package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator
package compiling

import com.intellij.openapi.util.io.FileUtil.loadFile
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.{PsiElement, PsiFile}
import org.assertj.core.api.Assertions.fail
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.runners._
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

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
        fail(s"Expected 4 sections: fileText, codeFragment, additionalImports, resultText; got: ${array.length}")
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

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
final class GeneratedClassTest extends GeneratedClassTestBase {
  def testFromPattern(): Unit = doTest()

  def testInConstructor(): Unit = doTest()

  def testInForStmt(): Unit = doTest()

  def testInLambda(): Unit = doTest()

  def testSimplePlace(): Unit = doTest()
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
final class GeneratedClassTest_Scala_3 extends GeneratedClassTestBase {
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
