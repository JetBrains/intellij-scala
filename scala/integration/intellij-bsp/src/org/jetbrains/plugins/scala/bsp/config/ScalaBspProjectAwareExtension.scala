package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.config.BspProjectAwareExtension
import scala.jdk.CollectionConverters._

import java.util
class ScalaBspProjectAwareExtension extends BspProjectAwareExtension{
  override def getEligibleConfigFileExtensions: util.List[String] =
    ScalaPluginConstants.SUPPORTED_EXTENSIONS.asJava

  override def getEligibleConfigFileNames: util.List[String] =
    ScalaPluginConstants.SUPPORTED_CONFIG_FILE_NAMES.asJava

  override def getProjectId(virtualFile: VirtualFile): ExternalSystemProjectId =
    new ExternalSystemProjectId(ScalaPluginConstants.SYSTEM_ID, virtualFile.getPath)
}
