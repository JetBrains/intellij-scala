package org.jetbrains.jps.incremental.scala.model
package impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.{JDOMUtil, Key}
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.{ScalaJpsProjectMetadata, ScalaJpsProjectMetadataConstants, SettingsManager}

import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.Try

private final class JpsScalaProjectMetadataExtensionServiceImpl extends JpsScalaProjectMetadataExtensionService {

  import JpsScalaProjectMetadataExtensionServiceImpl.{Log, ScalaJpsProjectMetadataKey}

  override def projectMetadata(context: CompileContext): ScalaJpsProjectMetadata = loadProjectMetadata(context)

  private def loadProjectMetadata(context: CompileContext): ScalaJpsProjectMetadata = {
    val alreadyComputedProjectMetadata = ScalaJpsProjectMetadataKey.get(context)
    if (alreadyComputedProjectMetadata != null) return alreadyComputedProjectMetadata

    val forceManualCompute = JpsScalaProjectMetadataExtensionService.isCBH(context)

    val projectMetadata =
      if (forceManualCompute) manuallyComputeMetadataFallback(context)
      else {
        val paths = context.getProjectDescriptor.dataManager.getDataPaths
        val dataStorageDir = paths.getDataStorageDir
        val configFilePath = dataStorageDir.resolve(ScalaJpsProjectMetadataConstants.ScalaJpsProjectMetadataFileName)
        computeProjectMetadata(configFilePath, context)
      }

    ScalaJpsProjectMetadataKey.set(context, projectMetadata)
    projectMetadata
  }

  private def computeProjectMetadata(configFilePath: Path, context: CompileContext): ScalaJpsProjectMetadata =
    readProjectMetadataFromDisk(configFilePath) match {
      case Some(metadata) => metadata
      case None =>
        Log.info(s"Could not read the Scala JPS project metadata from $configFilePath, falling back to manually computing the data")
        manuallyComputeMetadataFallback(context)
    }

  private def readProjectMetadataFromDisk(configFilePath: Path): Option[ScalaJpsProjectMetadata] = {
    val rootElement = Try(JDOMUtil.load(configFilePath)).toOption
    rootElement.flatMap(ScalaJpsProjectMetadata.parseXml)
  }

  private def manuallyComputeMetadataFallback(context: CompileContext): ScalaJpsProjectMetadata = {
    val modules = context.getProjectDescriptor.getProject.getModules.asScala
    val modulesWithScalaSdk = modules.filter(SettingsManager.getScalaSdk(_).isDefined).map(_.getName).toSet
    ScalaJpsProjectMetadata(modulesWithScalaSdk)
  }
}

private object JpsScalaProjectMetadataExtensionServiceImpl {
  private val Log: Logger = Logger.getInstance(classOf[JpsScalaProjectMetadataExtensionServiceImpl])

  private val ScalaJpsProjectMetadataKey: Key[ScalaJpsProjectMetadata] = Key.create("scala.jps.project.metadata")
}
