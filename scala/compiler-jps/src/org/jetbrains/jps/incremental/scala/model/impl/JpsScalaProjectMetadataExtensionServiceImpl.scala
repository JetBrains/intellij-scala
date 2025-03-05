package org.jetbrains.jps.incremental.scala.model
package impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.{JDOMUtil, Key}
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.{ScalaJpsProjectMetadataConstants, SettingsManager}
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

private final class JpsScalaProjectMetadataExtensionServiceImpl extends JpsScalaProjectMetadataExtensionService {

  import JpsScalaProjectMetadataExtensionServiceImpl.{Log, ModulesWithScalaSdkKey}

  override def projectHasScala(context: CompileContext): Boolean =
    loadConfig(context).nonEmpty

  override def moduleHasScala(context: CompileContext)(module: JpsModule): Boolean = {
    val name = module.getName
    loadConfig(context).contains(name)
  }

  private def loadConfig(context: CompileContext): Set[String] = {
    val alreadyComputedModules = context.getUserData(ModulesWithScalaSdkKey)
    if (alreadyComputedModules ne null) return alreadyComputedModules

    val forceManualSearch = JpsScalaProjectMetadataExtensionService.isCBH(context)

    val modulesWithScalaSdk =
      if (forceManualSearch) manualSearchFallback(context)
      else {
        val paths = context.getProjectDescriptor.dataManager.getDataPaths
        val dataStorageDir = paths.getDataStorageDir
        val configFilePath = dataStorageDir.resolve(ScalaJpsProjectMetadataConstants.ScalaJpsProjectMetadataFileName)
        computeConfig(configFilePath, context)
      }

    context.putUserData(ModulesWithScalaSdkKey, modulesWithScalaSdk)
    modulesWithScalaSdk
  }

  private def computeConfig(configFilePath: Path, context: CompileContext): Set[String] =
    try readConfigFromDisk(configFilePath)
    catch {
      case NonFatal(t) =>
        Log.info(s"Failed to read the modules with a configured Scala SDK from $configFilePath, falling back to manual search", t)
        manualSearchFallback(context)
    }

  private def readConfigFromDisk(configFilePath: Path): Set[String] = {
    val element = JDOMUtil.load(configFilePath)
    element.getChild(ScalaJpsProjectMetadataConstants.ModulesWithScalaSdkElement)
      .getChildren(ScalaJpsProjectMetadataConstants.ModuleElement)
      .asScala
      .map(_.getAttributeValue(ScalaJpsProjectMetadataConstants.NameAttribute))
      .toSet
  }

  private def manualSearchFallback(context: CompileContext): Set[String] = {
    val modules = context.getProjectDescriptor.getProject.getModules.asScala
    modules.filter(SettingsManager.getScalaSdk(_).isDefined).map(_.getName).toSet
  }
}

private object JpsScalaProjectMetadataExtensionServiceImpl {
  private val Log: Logger = Logger.getInstance(classOf[JpsScalaProjectMetadataExtensionServiceImpl])

  private val ModulesWithScalaSdkKey: Key[Set[String]] = Key.create("jps.scala.modulesWithScalaSdk")
}
