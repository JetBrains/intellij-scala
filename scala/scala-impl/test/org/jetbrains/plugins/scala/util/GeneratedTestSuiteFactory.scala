package org.jetbrains.plugins.scala.util

import com.intellij.util.ThrowableRunnable
import junit.framework.{Test, TestSuite}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.junit.Ignore


abstract class GeneratedTestSuiteFactory {
  type TestData = GeneratedTestSuiteFactory.TestData
  type TD <: TestData

  def testData: Seq[TD]
  def makeActualTest(testData: TD): Test

  final def suite: TestSuite = {
    val testSuite = new TestSuite()
    testData.map(makeActualTest).foreach(testSuite.addTest)
    testSuite
  }

  protected final def testDataFromCode(code: String): TestData = SimpleTestData.fromCode(code)

  //noinspection JUnitMalformedDeclaration
  @Ignore
  protected class SimpleHighlightingActualTest(testData: TestData, minScalaVersion: ScalaVersion) extends ScalaLightCodeInsightFixtureTestCase with AssertionMatchers {
    this.setName(testData.testName)

    override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(GeneratedTestSuiteFactory)
    override protected def supportedIn(version: ScalaVersion): Boolean = version >= minScalaVersion

    override def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit = {
      if (!testData.isFailing)
        checkTextHasNoErrors(testData.testCode)
    }
  }

  //noinspection JUnitMalformedDeclaration
  @Ignore
  protected abstract class SimpleActualTest(testData: TestData, minScalaVersion: ScalaVersion) extends SimpleTestCase with AssertionMatchers {
    this.setName(testData.testName)

    override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

    override def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit = runActualTest()
    def runActualTest(): Unit
  }

}

object GeneratedTestSuiteFactory {
  trait TestData {
    def testName: String
    def testCode: String
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

  case class TestDataErrorMessage(scalaPluginMessage: String, scalaCompilerMessage: String)

  case class SimpleTestData(override val testName: String,
                            override val testCode: String,
                            override val failureExpectation: Option[FailureExpectation]) extends TestData {
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

  abstract class withHighlightingTest(minScalaVersion: ScalaVersion) extends GeneratedTestSuiteFactory {
    override type TD = TestData
    final def makeActualTest(testData: TestData): Test = new SimpleHighlightingActualTest(testData, minScalaVersion)
  }
}