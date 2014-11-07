package org.jetbrains.plugins.scala
package project

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
class ScalaCompilerConfigurable(project: Project, settings: ScalaCompilerSettings) extends AbstractConfigurable("Scala Compiler")  {
  protected val form = new ScalaCompilerSettingsForm()

  def createComponent() = form.getComponent

  def isModified = form.getState != settings.getState

  def reset() {
    form.setState(settings.getState)
  }

  def apply() {
    settings.loadState(form.getState)
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}
