package org.jetbrains.plugins.scala
package project

import com.intellij.ProjectTopics
import com.intellij.openapi.project.{Project, ModuleAdapter}
import com.intellij.openapi.module.Module
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootAdapter}

/**
 * @author Pavel Fatin
 */
class ScalaProjectEvents(project: Project) extends AbstractProjectComponent(project) {
  private var listeners: List[ScalaProjectListener] = Nil

  private val connection = project.getMessageBus.connect()

  connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter {
    override def moduleRemoved(project: Project, module: Module) {
      update()
    }

    override def moduleAdded(project: Project, module: Module) {
      update()
    }
  })

  connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
    override def rootsChanged(event: ModuleRootEvent) {
      update()
    }
  })

  private def update() {
    if (project.hasScala) fireScalaAdded() else fireScalaRemoved()
  }

  def addScalaProjectListener(listener: ScalaProjectListener) {
    listeners ::= listener
  }

  def removeScalaProjectListener(listener: ScalaProjectListener) {
    listeners = listeners.filterNot(_ == listener)
  }

  private def fireScalaAdded() {
    listeners.foreach(_.onScalaAdded())
  }

  private def fireScalaRemoved() {
    listeners.foreach(_.onScalaRemoved())
  }

  override def disposeComponent() {
    connection.disconnect()
  }
}

trait ScalaProjectListener {
  def onScalaAdded()

  def onScalaRemoved()
}
