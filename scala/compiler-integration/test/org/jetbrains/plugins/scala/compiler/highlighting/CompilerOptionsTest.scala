package org.jetbrains.plugins.scala.compiler.highlighting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.{DynamicTest, TestFactory}

class CompilerOptionsTest {

  @TestFactory
  def fatalWarningsTests(): Array[DynamicTest] = {
    def assertFatalWarningsFlag(scalacOptions: Seq[String], expectedFlag: Boolean): Unit = {
      val actualFlag = CompilerOptions.containsFatalWarnings(scalacOptions)
      assertEquals(expectedFlag, actualFlag, s"Fatal warnings flag should be $expectedFlag for scalacOptions: $scalacOptions")
    }

    case class FatalWarningsTestCase(displayName: String, scalacOptions: Seq[String], expectedFlag: Boolean)

    Array(
      FatalWarningsTestCase(
        displayName = "fatalWarningsFlag",
        scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:implicits", "-Wnumeric-widen"),
        expectedFlag = true
      ),
      FatalWarningsTestCase(
        displayName = "werrorFlag",
        scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Werror", "-Wunused:implicits", "-Wnumeric-widen"),
        expectedFlag = true
      ),
      FatalWarningsTestCase(
        displayName = "noFlag",
        scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Wunused:implicits", "-Wnumeric-widen"),
        expectedFlag = false
      )
    ).map { case FatalWarningsTestCase(displayName, scalacOptions, expectedFlag) =>
      dynamicTest(displayName, () => assertFatalWarningsFlag(scalacOptions, expectedFlag))
    }
  }

  @TestFactory
  def unusedImportsTests(): Array[DynamicTest] = {
    def assertUnusedImportsFlag(scalacOptions: Seq[String], expectedFlag: Boolean): Unit = {
      val actualFlag = CompilerOptions.containsUnusedImports(scalacOptions)
      assertEquals(expectedFlag, actualFlag, s"Unused imports flag should be $expectedFlag for scalacOptions: $scalacOptions")
    }

    case class UnusedImportsTestCase(displayName: String, scalacOptions: Seq[String], expectedFlag: Boolean)

    Array(
      UnusedImportsTestCase(
        displayName = "manyUnusedFlagsIncludingImports",
        scalacOptions = Seq("-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:patvars,imports,privates", "-Wnumeric-widen", "-Wunused:implicits"),
        expectedFlag = true
      ),
      UnusedImportsTestCase(
        displayName = "unusedAllFlag",
        scalacOptions = Seq("-Wnumeric-widen", "-Wunused:all", "-Werror"),
        expectedFlag = true
      ),
      UnusedImportsTestCase(
        displayName = "unusedAllAndImports",
        scalacOptions = Seq("-Wunused:all,privates,imports,explicits", "-Werror"),
        expectedFlag = true
      ),
      UnusedImportsTestCase(
        displayName = "noUnusedImportsFlag",
        scalacOptions = Seq("-Wunused:patvars,privates", "-Wunused:locals,explicits", "-Wunused:implicits", "-Wnumeric-widen"),
        expectedFlag = false
      ),
      UnusedImportsTestCase(
        displayName = "repeateadUnusedImports",
        scalacOptions = Seq("-Wunused:imports,patvars,imports", "-Wunused:imports,explicits", "-Wunused:imports", "-Wnumeric-widen"),
        expectedFlag = true
      )
    ).map { case UnusedImportsTestCase(displayName, scalacOptions, expectedFlag) =>
      dynamicTest(displayName, () => assertUnusedImportsFlag(scalacOptions, expectedFlag))
    }
  }
}
