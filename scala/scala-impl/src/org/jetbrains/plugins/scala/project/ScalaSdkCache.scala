package org.jetbrains.plugins.scala
package project

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

/**
 * @author Pavel Fatin
 */
final class ScalaSdkCache(project: Project) extends ProjectComponent {

  project.subscribeToModuleRootChanged()(_ => clear())

  private val cache = new ConcurrentHashMap[Module, ScalaSdk]()

  override def projectClosed(): Unit = clear()

  def apply(module: Module): ScalaSdk = cache.computeIfAbsent(
    module,
    ScalaSdkCache.findScalaSdk(_)
  )

  private def clear(): Unit = cache.clear()
}

object ScalaSdkCache {

  def apply(project: Project): ScalaSdkCache =
    project.getComponent(classOf[ScalaSdkCache])

  private def findScalaSdk(module: Module): ScalaSdk = {
    var result: ScalaSdk = null

    ModuleRootManager.getInstance(module)
      .orderEntries()
      .recursively()
      .librariesOnly()
      .exportedOnly()
      .forEachLibrary { library =>
        val found = library.isScalaSdk

        if (found) result = new ScalaSdk(library)

        !found
      }

    result
  }
}