package org.jetbrains.plugins.scala.project

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.Processor

/**
 * @author Pavel Fatin
 */
class ScalaSdkCache(project: Project, events: ScalaProjectEvents) extends AbstractProjectComponent(project) {
  private val cache = new ConcurrentHashMap[Module, Option[ScalaSdk]]()

  events.addScalaProjectListener(new ScalaProjectListener {
    def onScalaProjectChanged() {
      cache.clear()
    }
  })

  override def projectClosed(): Unit = {
    cache.clear()
  }

  def get(module: Module): Option[ScalaSdk] = {
    val cached = cache.get(module)

    if (cached != null) cached
    else {
      val computed = scalaSdk0(module)
      cache.put(module, computed)
      computed
    }
  }

  private def scalaSdk0(module: Module): Option[ScalaSdk] = {
    var result: Option[ScalaSdk] = None

    // TODO breadth-first search is preferable
    val enumerator = ModuleRootManager.getInstance(module)
      .orderEntries().recursively().librariesOnly().exportedOnly()

    enumerator.forEachLibrary(new Processor[Library] {
      override def process(library: Library): Boolean = {
        if (library.isScalaSdk) {
          result = Some(new ScalaSdk(library))
          false
        } else {
          true
        }
      }
    })

    result
  }
}

object ScalaSdkCache {
  def instanceIn(project: Project): ScalaSdkCache =
    project.getComponent(classOf[ScalaSdkCache])
}