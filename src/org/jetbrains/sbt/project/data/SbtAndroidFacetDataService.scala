package org.jetbrains.sbt
package project.data


import java.io.File
import java.util

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.android.facet.{AndroidFacet, AndroidFacetType, AndroidRootUtil}

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 8/12/14.
 */
class SbtAndroidFacetDataService(val helper: ProjectStructureHelper)
        extends AbstractDataService[AndroidFacetData, AndroidFacet](AndroidFacetData.Key)
        with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[AndroidFacetData]], project: Project) {
    toImport.asScala.foreach { facetNode =>
      for {
        module <- getIdeModuleByNode(facetNode, project)
        facetManager <- Option(FacetManager.getInstance(module))
        facet = getOrCreateFacet(module, facetManager)
      } {
        configureFacet(module, facet, facetNode.getData)
      }
    }
  }

  private def getOrCreateFacet(module: Module, facetManager: FacetManager): AndroidFacet =
    Option(facetManager.getFacetByType(AndroidFacet.ID)).getOrElse(createFacet(module, facetManager))

  private def createFacet(module: Module, facetManager: FacetManager) = {
    val model = facetManager.createModifiableModel
    val facet = facetManager.createFacet(new AndroidFacetType, "Android", null)
    model.addFacet(facet)
    model.commit()
    facet
  }

  private def configureFacet(module: Module, facet: AndroidFacet, data: AndroidFacetData) = {
    val configuration = facet.getConfiguration.getState

    val base = AndroidRootUtil.getModuleDirPath(module)
    def getRelativePath(f: File) = "/" + FileUtil.getRelativePath(base, FileUtil.toSystemIndependentName(f.getAbsolutePath), '/')

    configuration.GEN_FOLDER_RELATIVE_PATH_APT = getRelativePath(data.gen)
    configuration.GEN_FOLDER_RELATIVE_PATH_AIDL = getRelativePath(data.gen)
    configuration.MANIFEST_FILE_RELATIVE_PATH = getRelativePath(data.manifest)
    configuration.RES_FOLDER_RELATIVE_PATH = getRelativePath(data.res)
    configuration.ASSETS_FOLDER_RELATIVE_PATH = getRelativePath(data.assets)
    configuration.LIBS_FOLDER_RELATIVE_PATH = getRelativePath(data.libs)
    configuration.APK_PATH = getRelativePath(data.apk)
    configuration.LIBRARY_PROJECT = data.isLibrary
    configuration.myProGuardCfgFiles = new util.ArrayList[String]()

    if (data.proguardConfig.nonEmpty) {
      val proguardFile = new File(module.getProject.getBasePath) / "proguard-sbt.txt"
      FileUtil.writeToFile(proguardFile, data.proguardConfig.mkString(SystemProperties.getLineSeparator))
      configuration.myProGuardCfgFiles.add(proguardFile.getCanonicalPath)
      configuration.RUN_PROGUARD = true
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

