package org.jetbrains.sbt
package project.data


import java.io.File
import java.util

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.facet.{AndroidFacet, AndroidFacetType, AndroidRootUtil}
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 8/12/14.
 */
class SbtAndroidFacetDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
        extends AbstractDataService[AndroidFacetData, AndroidFacet](AndroidFacetData.Key) {

  def doImportData(toImport: util.Collection[DataNode[AndroidFacetData]], project: Project) {
    toImport.asScala.foreach { facetNode =>
      val module = {
        val moduleData: ModuleData = facetNode.getData(ProjectKeys.MODULE)
        helper.findIdeModule(moduleData.getName, project)
      }

      val facetProperties = {
        val data  = facetNode.getData
        val props = new JpsAndroidModuleProperties
        val base  = new File(AndroidRootUtil.getModuleDirPath(module))
        props.GEN_FOLDER_RELATIVE_PATH_APT  = FileUtil.getRelativePath(base, data.gen)
        props.GEN_FOLDER_RELATIVE_PATH_AIDL = FileUtil.getRelativePath(base, data.gen)
        props.MANIFEST_FILE_RELATIVE_PATH   = FileUtil.getRelativePath(base, data.manifest)
        props.RES_FOLDER_RELATIVE_PATH      = FileUtil.getRelativePath(base, data.res)
        props.ASSETS_FOLDER_RELATIVE_PATH   = FileUtil.getRelativePath(base, data.assets)
        props.LIBS_FOLDER_RELATIVE_PATH     = FileUtil.getRelativePath(base, data.libs)
        props.APK_PATH = FileUtil.getRelativePath(base, data.apk)
        props.LIBRARY_PROJECT = data.isLibrary
        props
      }

      val facet = Option(FacetManager.getInstance(module)).flatMap { manager =>
        Option(manager.getFacetByType(AndroidFacet.ID))
      }

      def configure(facet: AndroidFacet) = facet.getConfiguration.loadState(facetProperties)

      def createFacet() {
        val facetManager = FacetManager.getInstance(module)
        val model = facetManager.createModifiableModel
        val facet = facetManager.createFacet(new AndroidFacetType, "Android", null)
        configure(facet)
        model.addFacet(facet)
        model.commit()
      }

      facet map configure getOrElse createFacet
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: AndroidFacet], project: Project) {
    toRemove.asScala.foreach { facet =>
      val facetManager = FacetManager.getInstance(facet.getModule)
      val model = facetManager.createModifiableModel
      model.removeFacet(facet)
      model.commit()
    }
  }
}

