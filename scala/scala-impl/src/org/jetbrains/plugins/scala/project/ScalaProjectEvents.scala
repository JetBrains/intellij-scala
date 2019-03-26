package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

import scala.collection.mutable

/**
 * @author Pavel Fatin
 */
final class ScalaProjectEvents(project: Project) extends ProjectComponent {

  private type Listener = () => Unit

  private var listeners = mutable.ListBuffer.empty[Listener]

  override def projectOpened(): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      listeners.foreach(_.apply())
    }
  }

  override def projectClosed(): Unit = {
    listeners = null
  }

  def addListener(listener: Listener): Unit = {
    listeners += listener
  }
}

