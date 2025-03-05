package org.jetbrains.jps.incremental.scala.model

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.BuildParameters
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait JpsScalaProjectMetadataExtensionService {

  /**
   * Check if any module in the project has a Scala SDK configured.
   *
   * @return `true` if any module in the project has a Scala SDK configured, `false` otherwise
   */
  def projectHasScala(context: CompileContext): Boolean

  /**
   * Check if the provided module has a Scala SDK configured.
   *
   * @return `true` if the provided module has a Scala SDK configured, `false` otherwise
   */
  def moduleHasScala(context: CompileContext)(module: JpsModule): Boolean
}

object JpsScalaProjectMetadataExtensionService {
  def instance(): JpsScalaProjectMetadataExtensionService =
    JpsServiceManager.getInstance().getService(classOf[JpsScalaProjectMetadataExtensionService])

  def moduleHasScala(context: CompileContext)(module: JpsModule): Boolean =
    instance().moduleHasScala(context)(module)

  def chunkHasScala(context: CompileContext)(chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.exists(moduleHasScala(context))

  def projectHasScala(context: CompileContext): Boolean =
    instance().projectHasScala(context)

  def isCBH(context: CompileContext): Boolean =
    Option(context.getBuilderParameter(BuildParameters.BuildTriggeredByCBH)).flatMap(_.toBooleanOption).getOrElse(false)
}
