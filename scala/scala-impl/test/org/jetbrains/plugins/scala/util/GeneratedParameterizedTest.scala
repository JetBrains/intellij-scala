package org.jetbrains.plugins.scala.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SingleCodeTestData
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
abstract class GeneratedSimpleParameterizedTest(minScalaVersion: ScalaVersion)
  extends SimpleTestCase with GeneratedParameterizedTestFactory {

  override def scalaCodeParsingFeatures: ScalaFeatures = ScalaFeatures.onlyByVersion(minScalaVersion)

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] = testParametersImpl

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def simpleParameterizedTest(@unused("used reflectively by the @TestCaseName annotation") testName: String, testData: TD): Unit = {
    runActualTest(testData)
  }

  def runActualTest(td: TD): Unit
}

@RunWith(classOf[JUnitParamsRunner])
abstract class GeneratedHighlightingParameterizedTest(minScalaVersion: ScalaVersion)
  extends ScalaLightCodeInsightFixtureTestCase with GeneratedParameterizedTestFactory {

  override type TD <: SingleCodeTestData

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] = testParametersImpl
  
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken((GeneratedParameterizedTestFactory, minScalaVersion))
  
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= minScalaVersion

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def highlightingParameterizedTest(@unused("used reflectively by the @TestCaseName annotation") testName: String, testData: TD): Unit = {
    checkTextHasNoErrors(testData)
  }

  /**
   * Similar to [[org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase#checkTextHasNoErrors]]
   * but uses a different way of checking if a test should pass (as provided by the test data instance).
   */
  private def checkTextHasNoErrors(testData: TD): Unit = {
    myFixture.configureByText(ScalaFileType.INSTANCE, testData.testCode)

    def doTestHighlighting(virtualFile: VirtualFile): Unit = {
      myFixture.testHighlighting(false, false, false, virtualFile)
    }

    try doTestHighlighting(getFile.getVirtualFile)
    catch {
      case e: AssertionError =>
        if (testData.isFailing) return // let the test pass if we expect it to fail
        throw e
    }
    assert(!testData.isFailing, "Test should fail, but it didn't")
  }
}

sealed trait GeneratedParameterizedTestFactory extends AssertionMatchers { self: UsefulTestCase =>

  type TestData = GeneratedParameterizedTestFactory.TestData
  type TD <: TestData

  def testData: Seq[TD]

  protected final def testParametersImpl: Array[AnyRef] =
    testData.toArray[TestData].map(td => Array(td.testName, td))
}

object GeneratedParameterizedTestFactory {
  
  final def testDataFromCode(code: String): SimpleTestData = SimpleTestData.fromCode(code)
  
  trait TestData {
    def testName: String

    def checkCodeFragment: String

    def failureExpectation: Option[FailureExpectation] = None

    final def isFailing: Boolean = failureExpectation.nonEmpty
  }

  sealed case class FailureExpectation(errors: Seq[TestDataError])(val linesCovered: Boolean, val messagesCovered: Boolean) {
    assert(!linesCovered || errors.forall(_.line.nonEmpty))
    assert(!messagesCovered || errors.forall(_.message.nonEmpty))
  }
  object FailureExpectation {
    def fromErrors(errors: Seq[TestDataError], linesCovered: Boolean = false, messagesCovered: Boolean = false): Option[FailureExpectation] =
      errors.nonEmpty.option(FailureExpectation(errors)(linesCovered, messagesCovered))
  }

  case class TestDataError(line: Option[Int], message: Option[TestDataErrorMessage]) {
    assert(line.nonEmpty || message.nonEmpty)
  }

  trait SingleCodeTestData extends TestData {
    def testCode: String
  }

  final case class TestDataErrorMessage(scalaPluginMessage: String, scalaCompilerMessage: String)

  final case class SimpleTestData(override val testName: String,
                                  override val testCode: String,
                                  override val failureExpectation: Option[FailureExpectation]) extends SingleCodeTestData {
    override def checkCodeFragment: String = testCode
  }

  object SimpleTestData {
    def fromCode(code: String): SimpleTestData = {
      val lines = code.strip.linesIterator.toSeq

      val testName = lines.head.trim.stripPrefix("//").trim
      assert(testName.nonEmpty)

      val errors =
        lines.zipWithIndex.collect {
          case (line, lineNum) if line.contains("// Error") =>
            TestDataError(Some(lineNum + 1), None)
        }

      SimpleTestData(testName, code.trim, FailureExpectation.fromErrors(errors, linesCovered = true))
    }
  }
}
