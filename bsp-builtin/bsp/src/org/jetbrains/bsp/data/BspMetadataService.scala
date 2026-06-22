package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModifiableRootModel, ModuleRootManager}
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByVersion, ScalaAbstractProjectDataService, SdkUtils}
import org.jetbrains.sbt.asPath

import java.util

class BspMetadataService extends ScalaAbstractProjectDataService[BspMetadata, Module](BspMetadata.Key) {

  override def importData(
    toImport: util.Collection[? <: DataNode[BspMetadata]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = executeProjectChangeAction {
    toImport.forEach { node =>
      doImport(node)(using project, modelsProvider)
    }
  }(using project)

  private def doImport(node: DataNode[BspMetadata])
                      (implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    modelsProvider.getIdeModuleByNode(node).foreach { module =>
      val data = node.getData
      val jdkByHome = Option(data.javaHome).map(u => JdkByHome(u.uri.asPath(using EelProviderUtil.getEelDescriptor(project))))
      val jdkByVersion = Option(data.javaVersion).map(JdkByVersion)
      val existingJdk = Option(ModuleRootManager.getInstance(module).getSdk)
      val moduleJdk = jdkByHome
        .orElse(jdkByVersion)
        .flatMap(SdkUtils.findOrCreateSdk(_, project))
        .orElse(existingJdk)

      val model = modelsProvider.getModifiableRootModel(module)
      model.inheritSdk()
      moduleJdk.foreach(model.setSdk)

      val moduleJdkVersion = moduleJdk.map(_.getVersionString)

      Option(data.languageLevel)
        .orElse(Option(data.javaVersion).map(LanguageLevel.parse)) // Fallback to javaVersion
        .orElse(moduleJdkVersion.map(LanguageLevel.parse)) // Fallback to the version of javaHome
        .flatMap(versionString => Option(versionString))
        .foreach(setLanguageLevel(model, _))
    }
  }

  private def setLanguageLevel(model: ModifiableRootModel, languageLevel: LanguageLevel)
                              (implicit project: Project): Unit = executeProjectChangeAction {
    val languageLevelExtension = model.getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
    languageLevelExtension.setLanguageLevel(languageLevel)
  }
}
