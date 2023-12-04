package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.TestData

abstract class CheckTestDataTestBase(testData: Seq[TestData]) extends ScalaCompilerTestBase {
  def this(factory: GeneratedTestSuiteFactory) = this(factory.testData)

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  def completeTestCode: String =
    testData.zipWithIndex.map{
      case (data, idx) =>
        s"""
           |object CompiledCheckTest$idx {
           |  // ${data.testName}
           |  ${data.checkCodeFragment.indent(2)}
           |}
           |""".stripMargin
    }.mkString("\n\n")

  def test(): Unit = runWithErrorsFromCompiler(getProject) {
    addFileToProjectSources("test.scala", completeTestCode)
    compiler.make().assertNoProblems(allowWarnings = true)
  }
}
