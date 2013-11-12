package org.jetbrains.sbt
package project

import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.project.Project
import collection.JavaConverters._
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.facet.FacetManager
import org.jetbrains.plugins.scala.config.{LibraryId, LibraryLevel, ScalaFacet}
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}

/**
 * @author Pavel Fatin
 */
class SbtFacetDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaFacetData, ScalaFacet](ScalaFacetData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaFacetData]], project: Project) {
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

  def doRemoveData(toRemove: util.Collection[_ <: ScalaFacet], project: Project) {
    toRemove.asScala.foreach { facet =>
      val facetManager = FacetManager.getInstance(facet.getModule)
      val model = facetManager.createModifiableModel
      model.removeFacet(facet)
      model.commit()
    }
  }
}
