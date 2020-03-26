package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter, Importer, JdkByHome, JdkByVersion, SdkUtils}
import org.jetbrains.bsp.BspUtil._

class BspMetadataService extends AbstractDataService[BspMetadata, Module](BspMetadata.Key) {

  override def createImporter(toImport: Seq[DataNode[BspMetadata]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[BspMetadata] =
    new AbstractImporter[BspMetadata](toImport, projectData, project, modelsProvider) {
      override def importData(): Unit =
        toImport.foreach(doImport)

      private def doImport(node: DataNode[BspMetadata]) = {
        getIdeModuleByNode(node).map { module =>
          val data = node.getData
          val jdkByHome = Option(data.javaHome).map(_.toFile).map(JdkByHome)
          val jdkByVersion = Option(data.javaVersion).map(JdkByVersion)
          val existingJdk = Option(ModuleRootManager.getInstance(module).getSdk)
          val moduleJdk = jdkByHome
            .orElse(jdkByVersion)
            .flatMap(SdkUtils.findProjectSdk)
            .orElse(existingJdk)

          val model = getModifiableRootModel(module)
          model.inheritSdk()
          moduleJdk.foreach(model.setSdk)
        }
      }

    }

}