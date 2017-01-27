package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.data.SbtModuleData

/**
  * Created by jast on 2017-01-24.
  */
class SbtModuleDataService extends AbstractDataService[SbtModuleData, Module](SbtModuleData.Key) {
  override def createImporter(toImport: Seq[DataNode[SbtModuleData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[SbtModuleData] =

    new AbstractImporter[SbtModuleData](toImport, projectData, project, modelsProvider) {
      override def importData(): Unit = ()
    }
}
