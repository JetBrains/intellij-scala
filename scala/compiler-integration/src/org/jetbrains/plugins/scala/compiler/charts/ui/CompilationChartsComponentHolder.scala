package org.jetbrains.plugins.scala.compiler.charts.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service
final class CompilationChartsComponentHolder {
  private var mainComponent: Option[CompilationChartsComponent] = None

  private def createOrGet(project: Project): CompilationChartsComponent = synchronized {
    mainComponent.getOrElse {
      val component = new CompilationChartsComponent(project)
      component.updateData()
      mainComponent = Some(component)
      component
    }
  }
}

object CompilationChartsComponentHolder {

  def createOrGet(project: Project): CompilationChartsComponent =
    project.getService(classOf[CompilationChartsComponentHolder]).createOrGet(project)
}
