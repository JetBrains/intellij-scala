package org.jetbrains.plugins.scala.compiler.highlighting

import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class CompilerOptionsTest {

  case class FatalWarningsTestParams(displayName: String, scalacOptions: Seq[String], expectedFlag: Boolean) {
    override def toString: String = displayName
  }

  @unused("used reflectively by the @Parameters annotation")
  private def fatalWarningsTestParameters: Array[FatalWarningsTestParams] = Array(
    FatalWarningsTestParams(
      displayName = "fatalWarningsFlag",
      scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:implicits", "-Wnumeric-widen"),
      expectedFlag = true
    ),
    FatalWarningsTestParams(
      displayName = "werrorFlag",
      scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Werror", "-Wunused:implicits", "-Wnumeric-widen"),
      expectedFlag = true
    ),
    FatalWarningsTestParams(
      displayName = "noFlag",
      scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Wunused:implicits", "-Wnumeric-widen"),
      expectedFlag = false
    )
  )

  @Test
  @Parameters(method = "fatalWarningsTestParameters")
  @TestCaseName(value = "{method}[{0}]")
  def fatalWarningsTest(params: FatalWarningsTestParams): Unit = {
    val FatalWarningsTestParams(_, scalacOptions, expectedFlag) = params
    val actualFlag = CompilerOptions.containsFatalWarnings(scalacOptions)
    assertEquals(s"Fatal warnings flag should be $expectedFlag for scalacOptions: $scalacOptions", expectedFlag, actualFlag)
  }

  case class UnusedImportsTestParams(displayName: String, scalacOptions: Seq[String], expectedFlag: Boolean) {
    override def toString: String = displayName
  }

  @unused("used reflectively by the @Parameters annotation")
  private def unusedImportsTestParameters: Array[UnusedImportsTestParams] = Array(
    UnusedImportsTestParams(
      displayName = "manyUnusedFlagsIncludingImports",
      scalacOptions = Seq("-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:patvars,imports,privates", "-Wnumeric-widen", "-Wunused:implicits"),
      expectedFlag = true
    ),
    UnusedImportsTestParams(
      displayName = "unusedAllFlag",
      scalacOptions = Seq("-Wnumeric-widen", "-Wunused:all", "-Werror"),
      expectedFlag = true
    ),
    UnusedImportsTestParams(
      displayName = "unusedAllAndImports",
      scalacOptions = Seq("-Wunused:all,privates,imports,explicits", "-Werror"),
      expectedFlag = true
    ),
    UnusedImportsTestParams(
      displayName = "noUnusedImportsFlag",
      scalacOptions = Seq("-Wunused:patvars,privates", "-Wunused:locals,explicits", "-Wunused:implicits", "-Wnumeric-widen"),
      expectedFlag = false
    ),
    UnusedImportsTestParams(
      displayName = "repeateadUnusedImports",
      scalacOptions = Seq("-Wunused:imports,patvars,imports", "-Wunused:imports,explicits", "-Wunused:imports", "-Wnumeric-widen"),
      expectedFlag = true
    )
  )

  @Test
  @Parameters(method = "unusedImportsTestParameters")
  @TestCaseName(value = "{method}[{0}]")
  def unusedImportsTest(params: UnusedImportsTestParams): Unit = {
    val UnusedImportsTestParams(_, scalacOptions, expectedFlag) = params
    val actualFlag = CompilerOptions.containsUnusedImports(scalacOptions)
    assertEquals(s"Unused imports flag should be $expectedFlag for scalacOptions: $scalacOptions", expectedFlag, actualFlag)
  }
}
