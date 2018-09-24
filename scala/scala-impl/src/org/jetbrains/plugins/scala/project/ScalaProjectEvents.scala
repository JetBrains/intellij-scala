package org.jetbrains.plugins.scala
package project

import com.intellij.ProjectTopics
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}

/**
 * @author Pavel Fatin
 */
class ScalaProjectEvents(project: Project) extends ProjectComponent {
  private var listeners: List[ScalaProjectListener] = Nil

  private val connection = project.getMessageBus.connect()

  override def projectOpened(): Unit = {
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      override def rootsChanged(event: ModuleRootEvent) {
        listeners.foreach(_.onScalaProjectChanged())
      }
    })
  }

  override def projectClosed() {
    listeners = Nil
    connection.disconnect()
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
