package org.jetbrains.plugins.scala.compiler.testUtils

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.RevertableChange

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Try

object CompilerTestUtil2 {

  private def compileServerSettings: ScalaCompileServerSettings =
    ScalaCompileServerSettings.getInstance().ensuring(
      _ != null,
      "could not get instance of compileServerSettings. Was plugin artifact built before running test?"
    )

  def withModifiedCompileServerSettings(body: ScalaCompileServerSettings => Unit): RevertableChange = new RevertableChange {
    private var settingsBefore: ScalaCompileServerSettings = _
    private lazy val settings: ScalaCompileServerSettings = compileServerSettings

    import com.intellij.java.testFramework.backend.{CompilerTestUtil => BackendCompilerTestUtil}

    override def applyChange(): Unit = {
      settingsBefore = XmlSerializerUtil.createCopy(settings)
      body(settings)
      BackendCompilerTestUtil.saveApplicationComponent(settings)
    }

    override def revertChange(): Unit = {
      XmlSerializerUtil.copyBean(settingsBefore, settings)
      BackendCompilerTestUtil.saveApplicationComponent(settings)
    }
  }

  def applyEnabledCompileServerSettings(settings: ScalaCompileServerSettings, enable: Boolean): Unit = {
    settings.COMPILE_SERVER_ENABLED = enable
    settings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    settings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
  }

  def withEnabledCompileServer(enable: Boolean): RevertableChange =
    withModifiedCompileServerSettings { settings =>
      applyEnabledCompileServerSettings(settings, enable)
    }

  def withForcedJdkForBuildProcess(optJdk: Option[Sdk]): RevertableChange = new RevertableChange {
    private var jdkBefore: Option[String] = None

    override def applyChange(): Unit = {
      optJdk.foreach { jdk =>
        jdk.getHomeDirectory match {
          case null =>
            throw new RuntimeException(s"Failed to set up JDK, got: $jdk")
          case homeDirectory =>
            val jdkHome = homeDirectory.getCanonicalPath
            //see com.intellij.compiler.server.BuildManager.COMPILER_PROCESS_JDK_PROPERTY
            val registry = Registry.get("compiler.process.jdk")
            jdkBefore = Try(registry.asString).toOption
            registry.setValue(jdkHome)
        }
      }
    }

    override def revertChange(): Unit =
      jdkBefore.foreach { jdk =>
        Registry.get("compiler.process.jdk").setValue(jdk)
      }
  }

  def assertProjectCompiles(project: Project, module: Module): Unit =
    assertProjectCompiles(project, Seq(module))

  def assertProjectCompiles(project: Project, module: Module, enableCompileServer: Boolean): Unit =
    assertProjectCompiles(project, Seq(module), enableCompileServer)

  def assertProjectCompiles(project: Project, modules: Seq[Module], enableCompileServer: Boolean = true): Unit = {
    modules.foreach(ensureModuleFileExists)

    val compiler = new SdkStateRestoringCompilerTester(project, modules)
    try {
      withEnabledCompileServer(enableCompileServer).run {
        CompilerMessagesUtil.assertNoErrors(compiler.make())
      }
    } finally {
      compiler.tearDown()
    }
  }

  // Without this method the compiler tester will fail with error
  // java.lang.AssertionError: File does not exist: .../light_idea_test_case.iml
  private def ensureModuleFileExists(module: Module): Unit = {
    val moduleFile = module.getModuleNioFile
    if (!Files.exists(moduleFile)) {
      Files.createDirectories(moduleFile.getParent)
      val dummyModuleFileContent =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<module type="JAVA_MODULE" version="4" />
          |""".stripMargin
      Files.writeString(moduleFile, dummyModuleFileContent, StandardCharsets.UTF_8)
    }
  }
}
