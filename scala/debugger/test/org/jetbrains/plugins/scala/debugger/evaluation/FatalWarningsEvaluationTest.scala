package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ModuleExt
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
abstract class FatalWarningsEvaluationTestBase extends EvaluationTestBase {

  addSourceFile("UnusedConcurrentHashMapImport.scala",
    s"""import java.util.concurrent.ConcurrentHashMap
       |
       |@main
       |def unusedConcurrentHashMapImport(): Unit =
       |  println() $breakpoint
       |""".stripMargin)

  @Test
  def wError(): Unit = {
    runFatalWarningsTest("-Werror")
  }

  @Test
  def xFatalWarnings(): Unit = {
    runFatalWarningsTest("-Xfatal-warnings")
  }

  private def runFatalWarningsTest(scalacOption: String): Unit = {
    addCompilerOptions(getModule, Seq("-Wunused:imports", scalacOption))

    expressionEvaluationTest("unusedConcurrentHashMapImport") { implicit ctx =>
      evalEquals("List.empty.exists(_ => true)", "false")
    }
  }

  private def addCompilerOptions(module: Module, additionalCompilerOptions: Seq[String]): Unit = {
    val profile = module.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
    profile.setSettings(newSettings)
  }
}

class FatalWarningsEvaluationTest_3 extends FatalWarningsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class FatalWarningsEvaluationTest_3_7 extends FatalWarningsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_7
}

class FatalWarningsEvaluationTest_RC extends FatalWarningsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class FatalWarningsEvaluationTest_Next_RC extends FatalWarningsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}
