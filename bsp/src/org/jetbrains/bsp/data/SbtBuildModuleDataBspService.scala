package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter}
import org.jetbrains.sbt.project.module

/**
  * @author Jason Zaugg
  */
final class SbtBuildModuleDataBspService extends AbstractDataService[SbtBuildModuleDataBsp, Module](SbtBuildModuleDataBsp.Key) {

  //noinspection TypeAnnotation
  override def createImporter(toImport: Seq[DataNode[SbtBuildModuleDataBsp]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider) =
    new AbstractImporter[SbtBuildModuleDataBsp](toImport, projectData, project, modelsProvider) {

      override def importData(): Unit = for {
        moduleNode <- dataToImport
        module <- getIdeModuleByNode(moduleNode)
      } SbtBuildModuleDataBspService.importData(module, moduleNode.getData)
    }
}

object SbtBuildModuleDataBspService {

  private def importData(sbtModule: Module,
                         data: SbtBuildModuleDataBsp): Unit = {
    import module.SbtModule._
    val SbtBuildModuleDataBsp(imports, buildFor) = data

    Imports(sbtModule) = imports
    buildFor.forEach(uri => Build(sbtModule) = uri)
  }
}
