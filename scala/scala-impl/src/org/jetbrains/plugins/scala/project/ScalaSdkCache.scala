package org.jetbrains.plugins.scala
package project

import java.{util => ju}

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.{OrderEnumerator, libraries}
import com.intellij.util.CommonProcessors.FindProcessor

/**
 * @author Pavel Fatin
 */
final class ScalaSdkCache(project: Project) extends ProjectComponent {

  project.subscribeToModuleRootChanged()(_ => clear())

  private val cache = new ju.concurrent.ConcurrentHashMap[Module, LibraryEx]()

  override def projectClosed(): Unit = clear()

  def apply(module: Module): LibraryEx = cache.computeIfAbsent(
    module,
    ScalaSdkCache.findScalaSdk(_).asInstanceOf[LibraryEx]
  )

  private def clear(): Unit = cache.clear()
}

object ScalaSdkCache {

  def apply(project: Project): ScalaSdkCache =
    project.getComponent(classOf[ScalaSdkCache])

  private def findScalaSdk(module: Module) = {
    val processor: FindProcessor[libraries.Library] = _.isScalaSdk

    OrderEnumerator.orderEntries(module)
      .recursively
      .librariesOnly
      .exportedOnly
      .forEachLibrary(processor)

    processor.getFoundValue
  }
}