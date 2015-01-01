package org.jetbrains.plugins.scala.project

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project

import scala.collection.mutable

/**
 * @author Pavel Fatin
 */
class ScalaProjectCache(project: Project, events: ScalaProjectEvents) extends AbstractProjectComponent(project) {
  private val cache = new mutable.HashMap[AnyRef, AnyRef]()
  private val lock = new Object()

  events.addScalaProjectListener(new ScalaProjectListener {
    def onScalaProjectChanged() {
      lock.synchronized {
        cache.clear()
      }
    }
  })

  def getOrUpdate[K <: AnyRef, V <: AnyRef](key: K)(value: => V): V = {
    lock.synchronized {
      cache.getOrElseUpdate(key, value).asInstanceOf[V]
    }
  }
}

object ScalaProjectCache {
  def instanceIn(project: Project): ScalaProjectCache =
    project.getComponent(classOf[ScalaProjectCache])
}