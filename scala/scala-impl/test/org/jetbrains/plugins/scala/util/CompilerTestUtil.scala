package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode, ScalaProjectSettings}

import scala.util.Try

object CompilerTestUtil {

  private def compileServerSettings: ScalaCompileServerSettings =
    ScalaCompileServerSettings.getInstance().ensuring(
      _ != null,
      "could not get instance of compileServerSettings. Was plugin artifact built before running test?"
    )

  def withModifiedCompileServerSettings(body: ScalaCompileServerSettings => Unit): RevertableChange = new RevertableChange {
    private var settingsBefore: ScalaCompileServerSettings = _
    private lazy val settings: ScalaCompileServerSettings = compileServerSettings

    override def applyChange(): Unit = {
      settingsBefore = XmlSerializerUtil.createCopy(settings)
      body(settings)
      com.intellij.compiler.CompilerTestUtil.saveApplicationComponent(settings)
    }

    override def revertChange(): Unit = {
      XmlSerializerUtil.copyBean(settingsBefore, settings)
      com.intellij.compiler.CompilerTestUtil.saveApplicationComponent(settings)
    }
  }

  def withEnabledCompileServer(enable: Boolean): RevertableChange = {
    val settings = compileServerSettings
    val r1 = RevertableChange.withModifiedSetting[Boolean](
      settings.COMPILE_SERVER_ENABLED,
      settings.COMPILE_SERVER_ENABLED = _,
      enable
    )
    val r2 = RevertableChange.withModifiedSetting[Boolean](
      settings.COMPILE_SERVER_SHUTDOWN_IDLE,
      settings.COMPILE_SERVER_SHUTDOWN_IDLE = _,
      true
    )
    val r3 = RevertableChange.withModifiedSetting[Int](
      settings.COMPILE_SERVER_SHUTDOWN_DELAY,
      settings.COMPILE_SERVER_SHUTDOWN_DELAY = _,
      30
    )
    r1 |+| r2 |+| r3
  }

  def withForcedJdkForBuildProcess(jdk: Sdk): RevertableChange = new RevertableChange {
    private var jdkBefore: Option[String] = None

    override def applyChange(): Unit = {
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

    override def revertChange(): Unit =
      jdkBefore.foreach { jdk =>
        Registry.get("compiler.process.jdk").setValue(jdk)
      }
  }

  def withCompileServerJdk(sdk: Sdk): RevertableChange = {
    val settings = compileServerSettings
    val r1 = RevertableChange.withModifiedSetting[Boolean](
      settings.USE_DEFAULT_SDK,
      settings.USE_DEFAULT_SDK = _,
      false
    )
    val r2 = RevertableChange.withModifiedSetting[String](
      settings.COMPILE_SERVER_SDK,
      settings.COMPILE_SERVER_SDK = _,
      sdk.getName
    )
    r1 |+| r2
  }

  private def withErrorsFromCompiler(project: Project, enabled: Boolean): RevertableChange = {
    val revertible1 = RevertableChange.withModifiedSetting(
      ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2,
      ScalaProjectSettings.getInstance(project).setCompilerHighlightingScala2(_),
      enabled
    )
    val revertible2 = RevertableChange.withModifiedSetting(
      ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3,
      ScalaProjectSettings.getInstance(project).setCompilerHighlightingScala3(_),
      enabled
    )
    val revertible3 = RevertableChange.withModifiedSetting[Boolean](
      ScalaHighlightingMode.compilerHighlightingEnabledInTests,
      ScalaHighlightingMode.compilerHighlightingEnabledInTests = _,
      enabled
    )
    revertible1 |+| revertible2 |+| revertible3
  }

  def runWithErrorsFromCompiler(project: Project)(body: => Unit): Unit = {
    val revertable: RevertableChange = withErrorsFromCompiler(project, enabled = true)
    revertable.run(body)
  }
}
