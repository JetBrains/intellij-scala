package org.jetbrains.plugins.scala.project

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
class ScalaProjectCache(project: Project, events: ScalaProjectEvents) extends AbstractProjectComponent(project) {
  private val cache = new ConcurrentHashMap[AnyRef, AnyRef]()

  events.addScalaProjectListener(new ScalaProjectListener {
    def onScalaProjectChanged() {
      cache.clear()
    }
  })

  def getOrUpdate[K <: AnyRef, V <: AnyRef](key: K)(value: => V): V = {
    Option(cache.get(key).asInstanceOf[V]).getOrElse {
      val result = value
      cache.put(key, result)
      result
    }
  }
}

object ScalaProjectCache {
  def instanceIn(project: Project): ScalaProjectCache =
    project.getComponent(classOf[ScalaProjectCache])
}