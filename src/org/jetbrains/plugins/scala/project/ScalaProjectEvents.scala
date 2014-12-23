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

  override def projectOpened()= {
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
  }

  override def projectClosed() {
    connection.disconnect()
  }

  override def disposeComponent() {
    connection.disconnect()
  }

  private def update() {
    listeners.foreach(_.onScalaProjectChanged())
  }

  def addScalaProjectListener(listener: ScalaProjectListener) {
    listeners ::= listener
  }

  def removeScalaProjectListener(listener: ScalaProjectListener) {
    listeners = listeners.filterNot(_ == listener)
  }
}

trait ScalaProjectListener {
  def onScalaProjectChanged()
}
