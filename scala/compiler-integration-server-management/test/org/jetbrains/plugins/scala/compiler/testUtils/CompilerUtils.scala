package org.jetbrains.plugins.scala.compiler.testUtils

import com.intellij.compiler.server.BuildManager
import com.intellij.java.testFramework.backend.CompilerTestUtil
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.{CompilerTester, EdtTestUtil}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert

import scala.jdk.CollectionConverters.CollectionHasAsScala

object CompilerUtils {

  def buildCrossProjectAndAssertNoWarningsOrErrors(project: Project): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    val incrementalityType = compilerConfiguration.incrementalityType
    Assert.assertEquals(
      s"Cross-built projects with shared sources should have ${IncrementalityType.SBT} incrementality type",
      IncrementalityType.SBT,
      incrementalityType
    )
    buildProjectAndAssertNoWarningsOrErrors(project)
  }

  def buildProjectAndAssertNoWarningsOrErrors(project: Project): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    val incrementalityType = compilerConfiguration.incrementalityType

    val modules = ModuleManager.getInstance(project).getModules
    val compiler = new CompilerTester(project, java.util.Arrays.asList(modules: _*), null, false)

    def buildMessageText(message: CompilerMessage): String = {
      s"""[${message.getCategory}] ${message.getVirtualFile}
         |${message.getMessage}""".stripMargin
    }

    try {
      val messages = compiler.rebuild().asScala.toSeq
      val warningsOrErrors: Seq[CompilerMessage] = messages.filter(m => Set(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING).contains(m.getCategory))
      Assert.assertEquals(
        s"Expecting no compilation warnings or errors (with ${incrementalityType} incremental compiler)",
        "",
        warningsOrErrors.map(buildMessageText).mkString("\n")
      )
    } finally {
      // Manually clean up compiler-related allocated resources to prevent resource leaks after test end
      compiler.tearDown()
      CompileServerLauncher.stopServerAndWait()
    }
  }

  def prepareExternalCompilerModel(project: Project): Unit = {
    // Make sure that the SDK settings are persisted to disk.
    // Without them, external Make can fail with "No JDK in module".
    // Saving it to disk will allow the JPS process to read it from there.
    //
    // NOTE: a similar thing is done in [[com.intellij.testFramework.CompilerTester.runCompiler]],
    // which also persists compiler inputs before invoking JPS (though CompilerTester.runCompiler does more than that).
    // For our needs this manual save is enough.
    //
    // Note that this test fixture can be used where the CompilerTester might not be used directly, hence the manual save.
    //
    // This is wired into [[org.jetbrains.sbt.runner.SbtRunConfiguration_LightExecution_TestBase]] and currently matters for
    // [[org.jetbrains.sbt.runner.SbtRunConfiguration_BuildBeforeLaunchTest]], which runs a real Build/Make before-launch step.
    EdtTestUtil.runInEdtAndWait { () =>
      CompilerTestUtil.saveApplicationSettings()
    }

    // Roots and SDKs are changed during fixture setup;
    // clear cached JPS state so the next external build sees the updated model.
    BuildManager.getInstance.clearState(project)
  }
}
