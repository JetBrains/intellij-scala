package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.impl.projectAware.BspProjectAwareExtension

import java.util
import scala.jdk.CollectionConverters._

class MillScalaBspProjectAwareExtension extends BspProjectAwareExtension {
  override def getEligibleConfigFileExtensions: util.List[String] =
    MillScalaPluginConstants.SUPPORTED_CONFIG_FILE_EXTENSIONS.asJava

  override def getEligibleConfigFileNames: util.List[String] =
    MillScalaPluginConstants.SUPPORTED_CONFIG_FILE_NAMES.asJava

  override def getProjectId(virtualFile: VirtualFile): ExternalSystemProjectId =
    new ExternalSystemProjectId(MillScalaPluginConstants.SYSTEM_ID, virtualFile.getPath)
}