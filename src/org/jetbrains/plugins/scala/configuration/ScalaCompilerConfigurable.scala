package org.jetbrains.plugins.scala
package configuration


/**
 * @author Pavel Fatin
 */
class ScalaCompilerConfigurable(settings: ScalaCompilerSettings) extends AbstractConfigurable("Scala Compiler")  {
  protected lazy val form = new ScalaCompilerSettingsForm()

  def createComponent() = form.getComponent

  def isModified = form.getState != settings.getState

  def reset() {
    form.setState(settings.getState)
  }

  def apply() {
    settings.loadState(form.getState)
  }
}
