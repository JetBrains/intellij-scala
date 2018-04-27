package org.jetbrains.sbt.project.data.service

import java.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter, Importer}
import org.jetbrains.sbt.project.data.Play2ProjectData

/**
 * User: Dmitry.Naydanov
 * Date: 14.11.14.
 */
class Play2StubDataService extends AbstractDataService[Play2ProjectData, Project](Play2ProjectData.Key) {
  override def createImporter(toImport: Seq[DataNode[Play2ProjectData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[Play2ProjectData] =
    new AbstractImporter[Play2ProjectData](toImport, projectData, project, modelsProvider) {
      override def importData(): Unit = {}
    }
}
