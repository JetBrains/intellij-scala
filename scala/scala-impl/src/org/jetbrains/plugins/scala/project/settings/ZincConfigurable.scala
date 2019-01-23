package org.jetbrains.plugins.scala.project.settings

import com.intellij.openapi.project.Project
import javax.swing.JComponent
import org.jetbrains.plugins.scala.project.{AbstractConfigurable, CompileToJarComponent}

class ZincConfigurable(project: Project, config: ZincConfiguration) extends AbstractConfigurable("Zinc") {

  private val form = new ZincConfigurationPanel(project)

  override def createComponent(): JComponent = form.contentPanel

  override def isModified: Boolean = {
    form.compileToJar != config.compileToJar ||
    form.enableIgnoringScalacOptions != config.enableIgnoringScalacOptions ||
    form.ignoredScalacOptions != config.ignoredScalacOptions
  }

  override def reset(): Unit = {
    form.compileToJar = config.compileToJar
    form.enableIgnoringScalacOptions = config.enableIgnoringScalacOptions
    form.ignoredScalacOptions = config.ignoredScalacOptions
  }

  override def apply(): Unit = {
    val newCompileToJar = form.compileToJar
    if (newCompileToJar != config.compileToJar) {
      CompileToJarComponent.getInstance(project).adjustClasspath(newCompileToJar)
    }
    config.compileToJar = newCompileToJar
    config.enableIgnoringScalacOptions = form.enableIgnoringScalacOptions
    config.ignoredScalacOptions = form.ignoredScalacOptions
  }

}
