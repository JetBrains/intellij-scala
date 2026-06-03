package org.jetbrains.jps.incremental.scala.model
package impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.{JDOMUtil, Key}
import com.intellij.util.containers.FileCollectionFactory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.model.impl.JpsScalaProjectMetadataExtensionServiceImpl.ParsedProjectMetadataInstance
import org.jetbrains.jps.incremental.scala.{BuildParameters, ScalaJpsProjectMetadata, ScalaJpsProjectMetadataConstants, SettingsManager}

import java.nio.file.Path
import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.jdk.CollectionConverters._
import scala.util.Try

private final class JpsScalaProjectMetadataExtensionServiceImpl extends JpsScalaProjectMetadataExtensionService {

  import JpsScalaProjectMetadataExtensionServiceImpl.Log

  private val loadedProjectMetadataInstances: java.util.Map[Path, ScalaJpsProjectMetadata] =
    FileCollectionFactory.createCanonicalPathMap()

  private val lock: Lock = new ReentrantLock()

  override def projectMetadata(context: CompileContext): ScalaJpsProjectMetadata = loadProjectMetadata(context)

  private def loadProjectMetadata(context: CompileContext): ScalaJpsProjectMetadata = {
    lock.lock()
    try {
      val readFromCommand = JpsScalaProjectMetadataExtensionService.isCBH(context)
      if (readFromCommand) {
        val alreadyParsedInstance = ParsedProjectMetadataInstance.get(context)
        if (alreadyParsedInstance != null) {
          // Avoids parsing the provided JSON multiple times for the same CompileContext instance.
          return alreadyParsedInstance
        }

        val compactJsonString = context.getBuilderParameter(BuildParameters.JpsProjectMetadataParameter)
        if (compactJsonString == null) {
          throw new IllegalStateException("ScalaJpsProjectMetadata was not provided with the CBH compilation request")
        }

        val projectMetadata = ScalaJpsProjectMetadata.parseCompactJsonString(compactJsonString)
        ParsedProjectMetadataInstance.set(context, projectMetadata)
        return projectMetadata
      }

      val filePath = projectMetadataFilePath(context)
      val alreadyComputedProjectMetadata = loadedProjectMetadataInstances.get(filePath)
      if (alreadyComputedProjectMetadata != null) return alreadyComputedProjectMetadata

      val projectMetadata = computeProjectMetadata(filePath, context)
      loadedProjectMetadataInstances.put(filePath, projectMetadata)
      projectMetadata
    } finally {
      lock.unlock()
    }
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

  private def projectMetadataFilePath(context: CompileContext): Path = {
    val dataStorageDir = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageDir
    dataStorageDir.resolve(ScalaJpsProjectMetadataConstants.ScalaJpsProjectMetadataFileName)
  }

  private def manuallyComputeMetadataFallback(context: CompileContext): ScalaJpsProjectMetadata = {
    val modules = context.getProjectDescriptor.getProject.getModules.asScala
    val modulesWithScalaSdk = modules.filter(SettingsManager.getScalaSdk(_).isDefined).map(_.getName).toSet
    ScalaJpsProjectMetadata(modulesWithScalaSdk, useModuleDisplayName = false)
  }
}

private object JpsScalaProjectMetadataExtensionServiceImpl {
  private val Log: Logger = Logger.getInstance(classOf[JpsScalaProjectMetadataExtensionServiceImpl])

  private final val ParsedProjectMetadataInstance: Key[ScalaJpsProjectMetadata] =
    Key.create("scala.jps.project.metadata.parsed.instance")
}
