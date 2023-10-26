package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.lang.psi.GenerateGivenNameTest.GivenNameTestData
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler

// checks if the test data in GenerateGivenNameTest.allTests is correct
class CheckGenerateGivenNameTestDataTest extends ScalaCompilerTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  def test(): Unit = runWithErrorsFromCompiler(getProject) {
    val code = GenerateGivenNameTest.allTests.zipWithIndex.map {
      case (GivenNameTestData(code, expected), idx) =>
        val testObj = s"Test$idx"
        s"""
           |object $testObj {
           |  ${code.trim.replace("\n", "\n  ")}
           |}
           |println($testObj.`$expected`)
           |""".stripMargin
    }.mkString("\n")

    addFileToProjectSources(
      "test.scala",
      s"""
         |object TheTest {
         |  ${code.trim.replace("\n", "\n  ")}
         |}
         |""".stripMargin
    )
    compiler.make().assertNoProblems(allowWarnings = true)
  }
}
