package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModifiableRootModel, ModuleRootManager}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByVersion, ScalaAbstractProjectDataService, SdkUtils}

import java.util

class BspMetadataService extends ScalaAbstractProjectDataService[BspMetadata, Module](BspMetadata.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[BspMetadata]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = executeProjectChangeAction {
    toImport.forEach { node =>
      doImport(node)(project, modelsProvider)
    }
  }(project)

  private def doImport(node: DataNode[BspMetadata])
                      (implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    modelsProvider.getIdeModuleByNode(node).foreach { module =>
      val data = node.getData
      val jdkByHome = Option(data.javaHome).map(_.uri.toFile).map(JdkByHome)
      val jdkByVersion = Option(data.javaVersion).map(JdkByVersion)
      val existingJdk = Option(ModuleRootManager.getInstance(module).getSdk)
      val moduleJdk = jdkByHome
        .orElse(jdkByVersion)
        .flatMap(SdkUtils.findOrCreateSdk)
        .orElse(existingJdk)

      val model = modelsProvider.getModifiableRootModel(module)
      model.inheritSdk()
      moduleJdk.foreach(model.setSdk)

      val moduleJdkVersion = moduleJdk.map(_.getVersionString)

      Option(data.languageLevel)
        .orElse(moduleJdkVersion.map(LanguageLevel.parse))
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
