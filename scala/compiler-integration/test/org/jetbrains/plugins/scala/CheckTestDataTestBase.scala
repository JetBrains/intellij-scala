package org.jetbrains.plugins.scala

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.TestData
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.IterableHasAsScala

@RunWith(classOf[JUnit4])
@Category(Array(classOf[CompilerHighlightingTests]))
abstract class CheckTestDataTestBase(testData: Seq[TestData], minScalaVersion: ScalaVersion)
  extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= minScalaVersion

  private def wrapIntoObject(x: (TestData, Int)): String = {
    val (data, idx) = x

    s"""
       |object CompiledCheckTest$idx {
       |  // ${data.testName}
       |  ${data.checkCodeFragment.indent(2)}
       |}
       |""".stripMargin.trim
  }

  def buildCompleteSucceedingTestCode(): String =
    testData
      .filterNot(_.isFailing)
      .zipWithIndex
      .map(wrapIntoObject)
      .mkString("\n\n")

  @Test
  def test(): Unit = {
    assert(testData.nonEmpty)
    if (testData.forall(_.isFailing)) {
      return
    }

    runWithErrorsFromCompiler(getProject) {
      addFileToProjectSources("test.scala", buildCompleteSucceedingTestCode())
      compiler.make().assertNoProblems(allowWarnings = true)
    }
  }
  @Test
  def test_failing(): Unit = {
    assert(testData.nonEmpty)
    val tests = testData.filter(_.isFailing)
    if (tests.isEmpty)
      return // quick return before doing any unnecessary work

    runWithErrorsFromCompiler(getProject) {
      // because of the boilerplate code, the line numbers in the compiler messages are off by two
      // that's why this class is private, it won't work in other places
      implicit class CompilerMessageExt(private val message: CompilerMessage) {
        def line: Int = message.asInstanceOf[CompilerMessageImpl].getLine - 2
      }

      for {
        case (test, idx) <- tests.zipWithIndex
        code = wrapIntoObject((test, idx))
      } {
        addFileToProjectSources(s"test${idx}_${test.testName}.scala", code)
      }

      val messages = compiler.make().asScala.toSeq
      val errors = messages
        .filter(_.getCategory == CompilerMessageCategory.ERROR)

      for (case (test, idx) <- tests.zipWithIndex) {
        val failureExpectation = test.failureExpectation.get
        val actualErrors = errors.filter(_.getVirtualFile.getName.contains(s"test${idx}_"))
        try {
          // expect at least one failure
          assert(
            actualErrors.nonEmpty,
            s"Expected to find errors, but found none"
          )

          val expectedErrors = failureExpectation.errors.filterNot(_.onlyForUs)
          for (expectedError <- expectedErrors) {
            for (expectedLine <- expectedError.line) {
              assert(
                actualErrors.exists(_.line == expectedLine),
                s"Expected to find an error in line $expectedLine, but only found errors in lines ${actualErrors.map(_.line).mkString(", ")}"
              )
            }

            for (expectedMessage <- expectedError.message.map(_.scalaCompilerMessage)) {
              assert(
                actualErrors.exists(_.getMessage.contains(expectedMessage)),
                s"Expected to find an error with message $expectedMessage, but only found errors with messages ${actualErrors.map(_.getMessage).mkString(", ")}"
              )
            }
          }

          if (failureExpectation.linesCovered) {
            val expectedLinesWithErrors = expectedErrors.map(_.line.get).toSet
            val actualLinesWithErrors = actualErrors.map(_.line).toSet
            assert(
              actualLinesWithErrors == expectedLinesWithErrors,
              s"Expected to find errors in lines ${expectedLinesWithErrors.mkString(", ")}, but also found errors in lines ${actualLinesWithErrors.mkString(", ")}"
            )
          }

          if (failureExpectation.messagesCovered) {
            val expectedMessagesWithErrors = expectedErrors.filterNot(_.onlyForUs).map(_.message.get.scalaCompilerMessage).toSet
            val actualMessagesWithErrors = actualErrors.map(_.getMessage).toSet
            assert(
              actualMessagesWithErrors == expectedMessagesWithErrors,
              s"Expected to find errors with messages ${expectedMessagesWithErrors.mkString(", ")}, but only found errors with messages ${actualMessagesWithErrors.mkString(", ")}"
            )
          }
        } catch {
          case e: Throwable =>
            throw new AssertionError(
              s"""Checking compiler errors in test$idx.scala (from test case ${test.testName}) failed:
                 |${e.getMessage}
                 |==== Compiler errors were ====
                 |${actualErrors.map(_.getMessage).mkString("\n")}
                 |""".stripMargin, e)
        }
      }
    }
  }
}
