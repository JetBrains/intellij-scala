package org.jetbrains.plugins.scala

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.TestData

import scala.jdk.CollectionConverters.IterableHasAsScala

abstract class CheckTestDataTestBase(testData: Seq[TestData]) extends ScalaCompilerTestBase {
  def this(factory: GeneratedTestSuiteFactory) = this(factory.testData)

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  private def wrapIntoObject(x: (TestData, Int)): String = {
    val (data, idx) = x

    s"""
       |object CompiledCheckTest$idx {
       |  // ${data.testName}
       |  ${data.checkCodeFragment.indent(2)}
       |}
       |""".stripMargin.trim
  }

  def test(): Unit = runWithErrorsFromCompiler(getProject) {
    val completeSucceedingTestCode: String =
      testData
        .filterNot(_.isFailing)
        .zipWithIndex
        .map(wrapIntoObject)
        .mkString("\n\n")
    addFileToProjectSources("test.scala", completeSucceedingTestCode)
    compiler.make().assertNoProblems(allowWarnings = true)
  }

  def test_failing(): Unit = {
    val tests = testData.filter(_.isFailing)
    if (tests.isEmpty)
      return // quick return before doing any unnecessary work

    runWithErrorsFromCompiler(getProject) {
      // because of the boilerplate code, the line numbers in the compiler messages are off by two
      // that's why this class is private, it won't work in other places
      implicit class CompilerMessageExt(private val message: CompilerMessage) {
        def line: Int = message.asInstanceOf[CompilerMessageImpl].getLine - 2
      }

      for (case (code, idx) <- tests.zipWithIndex.map(wrapIntoObject).zipWithIndex) {
        addFileToProjectSources(s"test$idx.scala", code)
      }

      val messages = compiler.make().asScala.toSeq
      val errors = messages
        .filter(_.getCategory == CompilerMessageCategory.ERROR)

      for (case (test, idx) <- tests.zipWithIndex) {
        val failureExpectation = test.failureExpectation.get
        val actualErrors = errors.filter(_.getVirtualFile.getName.contains(s"test$idx"))
        try {
          // expect at least one failure
          assert(
            actualErrors.nonEmpty,
            s"Expected to find errors, but found none"
          )

          for (expectedError <- failureExpectation.errors) {
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
            val expectedLinesWithErrors = failureExpectation.errors.map(_.line.get).toSet
            val actualLinesWithErrors = actualErrors.map(_.line).toSet
            assert(
              actualLinesWithErrors == expectedLinesWithErrors,
              s"Expected to find errors in lines ${expectedLinesWithErrors.mkString(", ")}, but also found errors in lines ${actualLinesWithErrors.mkString(", ")}"
            )
          }

          if (failureExpectation.messagesCovered) {
            val expectedMessagesWithErrors = failureExpectation.errors.map(_.message.get.scalaCompilerMessage).toSet
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
