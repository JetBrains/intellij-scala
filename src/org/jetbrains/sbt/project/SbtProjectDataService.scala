package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.project.Project
import collection.JavaConverters._
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.facet.FacetManager
import org.jetbrains.plugins.scala.config.{LibraryId, LibraryLevel, ScalaFacetConfiguration, ScalaFacet}
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper) extends ProjectDataService[ScalaFacetData, ScalaFacet] {
  def getTargetDataKey = ScalaFacetData.Key

  def importData(toImport: util.Collection[DataNode[ScalaFacetData]], project: Project, synchronous: Boolean) {
    toImport.asScala.foreach { facetNode =>
      val facetData = facetNode.getData

      val module = {
        val moduleData: ModuleData = facetNode.getData(ProjectKeys.MODULE)
        helper.findIdeModule(moduleData.getName, project)
      }

      ScalaFacet.findIn(module).map(configure(_, facetData))
        .getOrElse(ScalaFacet.createIn(module)(configure(_, facetData)))
    }
  }

  private def configure(facet: ScalaFacet, data: ScalaFacetData) {
    facet.compilerLibraryId = LibraryId(data.compilerLibraryName, LibraryLevel.Project)
    facet.compilerParameters = data.compilerOptions.toArray
  }

  def removeData(toRemove: util.Collection[_ <: ScalaFacet], project: Project, synchronous: Boolean) {
    toRemove.asScala.foreach { facet =>
      val facetManager = FacetManager.getInstance(facet.getModule)
      val model = facetManager.createModifiableModel
      model.removeFacet(facet)
      model.commit()
    }
  }
}
