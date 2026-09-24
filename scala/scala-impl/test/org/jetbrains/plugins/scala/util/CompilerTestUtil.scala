package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.scala.compiler.testUtils.CompilerTestUtil2
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode, ScalaProjectSettings}


object CompilerTestUtil {

  def withModifiedCompileServerSettings(body: ScalaCompileServerSettings => Unit): RevertableChange =
    CompilerTestUtil2.withModifiedCompileServerSettings(body)

  def applyEnabledCompileServerSettings(settings: ScalaCompileServerSettings, enable: Boolean): Unit =
    CompilerTestUtil2.applyEnabledCompileServerSettings(settings, enable)

  def withEnabledCompileServer(enable: Boolean): RevertableChange =
    CompilerTestUtil2.withEnabledCompileServer(enable)

  def withForcedJdkForBuildProcess(optJdk: Option[Sdk]): RevertableChange =
    CompilerTestUtil2.withForcedJdkForBuildProcess(optJdk)

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
