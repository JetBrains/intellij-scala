package org.jetbrains.plugins.scala.compilation

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.RevertableChange

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

  def withEnabledCompileServer(enable: Boolean): RevertableChange = withModifiedCompileServerSettings { settings =>
    settings.COMPILE_SERVER_ENABLED = enable
    settings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    settings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
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

  def withCompileServerJdk(sdk: Sdk): RevertableChange =
    withModifiedCompileServerSettings { settings =>
      settings.USE_DEFAULT_SDK = false
      settings.COMPILE_SERVER_SDK = sdk.getName
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
