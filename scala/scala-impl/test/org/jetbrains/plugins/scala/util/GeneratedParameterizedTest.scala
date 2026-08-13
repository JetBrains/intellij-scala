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
import org.junit.{Assert, ComparisonFailure, Test}
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

    try {
      doTestHighlighting(getFile.getVirtualFile)
    } catch {
      case e: ComparisonFailure =>
        testData.failureExpectation match {
          case Some(expectation) if expectation.linesCovered =>
            val linesWithActualErrors =
              e.getActual.linesIterator.zipWithIndex
                .collect { case (line, lineNum) if line.contains("<error ") => lineNum + 1 }
                .toSet
            val linesWithExpectedErrors = expectation.errors.map(_.line.get).toSet

            Assert.assertEquals(e.getActual, linesWithExpectedErrors, linesWithActualErrors)
            return
          case Some(_) =>
            // We only know there that there is supposed to be an error somewhere
            return
          case None =>
            // test was not supposed to have any errors
            throw e
        }
    }

    if (testData.isFailing) {
      Assert.fail(s"Expected a highlighting error, but got none.\n${testData.testCode}")
    }
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

  /**
   * Like [[testDataFromCode]], but for code that is shared between scala versions and marks the parts
   * that only apply to one of them with a `[Scala2]`/`[Scala3]` tag.
   * Every line that contains `removeTag` loses everything from its comment on, so that both
   * version-specific code and version-specific error expectations can be written in a single test case.
   */
  final def testDataFromVersionTaggedCode(removeTag: String)(code: String): SimpleTestData =
    testDataFromCode(
      code.linesIterator
        .map {
          case line if line.contains(removeTag) => line.take(line.indexOf("//").max(0))
          case line                             => line
        }
        .map(_.replace("[Scala2]", "").replace("[Scala3]", ""))
        .mkString("\n")
    )

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

  case class TestDataError(line: Option[Int], message: Option[TestDataErrorMessage], onlyForUs: Boolean) {
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
            TestDataError(Some(lineNum + 1), None, onlyForUs = line.contains("// Error(IntelliJ)"))
        }

      SimpleTestData(testName, code.trim, FailureExpectation.fromErrors(errors, linesCovered = true))
    }
  }
}
