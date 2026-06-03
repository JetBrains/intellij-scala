package org.jetbrains.jps.incremental.scala.model

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.{BuildParameters, ScalaJpsProjectMetadata}
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager

import java.util.UUID
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

trait JpsScalaProjectMetadataExtensionService {

  /**
   * Check if any module in the project has a Scala SDK configured.
   *
   * @return `true` if any module in the project has a Scala SDK configured, `false` otherwise
   */
  def projectHasScala(context: CompileContext): Boolean =
    modulesWithScala(context).nonEmpty

  /**
   * Check if the provided module has a Scala SDK configured.
   *
   * @return `true` if the provided module has a Scala SDK configured, `false` otherwise
   */
  def moduleHasScala(context: CompileContext)(module: JpsModule): Boolean =
    modulesWithScala(context).contains(module.getName)

  /**
   * All modules where a Scala SDK is configured.
   * 
   * @return A set of module names which have a Scala SDK configured.
   */
  def modulesWithScala(context: CompileContext): Set[String] =
    projectMetadata(context).modulesWithScalaSdk

  /**
   * Show shortened, display names of modules in the compiler messages instead of the full module names
   * which are longer and may contain a repeated root.
   */
  def useModuleDisplayName(context: CompileContext): Boolean =
    projectMetadata(context).useModuleDisplayName

  /**
   * Returns the project metadata for the given compile context.
   */
  def projectMetadata(context: CompileContext): ScalaJpsProjectMetadata
}

object JpsScalaProjectMetadataExtensionService {
  def instance(): JpsScalaProjectMetadataExtensionService =
    JpsServiceManager.getInstance().getService(classOf[JpsScalaProjectMetadataExtensionService])
    
  def modulesWithScala(context: CompileContext): Set[String] =
    instance().modulesWithScala(context)

  def moduleHasScala(context: CompileContext)(module: JpsModule): Boolean =
    instance().moduleHasScala(context)(module)

  def chunkHasScala(context: CompileContext)(chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.exists(moduleHasScala(context))

  def projectHasScala(context: CompileContext): Boolean =
    instance().projectHasScala(context)

  def useModuleDisplayName(context: CompileContext): Boolean =
    instance().useModuleDisplayName(context)

  def isCBH(context: CompileContext): Boolean =
    Option(context.getBuilderParameter(BuildParameters.BuildTriggeredByCBH)).flatMap(_.toBooleanOption).getOrElse(false)
    
  def customBuildId(context: CompileContext): Option[UUID] =
    Option(context.getBuilderParameter(BuildParameters.CustomBuildIdForCBH))
      .flatMap(str => Try(UUID.fromString(str)).toOption)
}
