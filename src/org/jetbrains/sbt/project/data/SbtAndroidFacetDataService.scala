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
class SbtAndroidFacetDataService(platformFacade: PlatformFacade, val helper: ProjectStructureHelper)
        extends AbstractDataService[AndroidFacetData, AndroidFacet](AndroidFacetData.Key)
        with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[AndroidFacetData]], project: Project) {
    toImport.asScala.foreach { facetNode =>
      for {
        module <- getIdeModuleByNode(facetNode, project)
        facetManager <- Option(FacetManager.getInstance(module))
        facet = Option(facetManager.getFacetByType(AndroidFacet.ID)).getOrElse(createFacet(module))
      } {
        configureFacet(facet, facetNode.getData)
      }
    }
  }

  private def createFacet(module: Module) = {
    val facetManager = FacetManager.getInstance(module)
    val model = facetManager.createModifiableModel
    val facet = facetManager.createFacet(new AndroidFacetType, "Android", null)
    model.addFacet(facet)
    model.commit()
    facet
  }

  private def configureFacet(facet: AndroidFacet, data: AndroidFacetData) = {
    val module = facet.getModule
    val props = facet.getConfiguration.getState

    val base = AndroidRootUtil.getModuleDirPath(module)
    def getRelativePath(f: File) = "/" + FileUtil.getRelativePath(base, FileUtil.toSystemIndependentName(f.getAbsolutePath), '/')

    props.GEN_FOLDER_RELATIVE_PATH_APT = getRelativePath(data.gen)
    props.GEN_FOLDER_RELATIVE_PATH_AIDL = getRelativePath(data.gen)
    props.MANIFEST_FILE_RELATIVE_PATH = getRelativePath(data.manifest)
    props.RES_FOLDER_RELATIVE_PATH = getRelativePath(data.res)
    props.ASSETS_FOLDER_RELATIVE_PATH = getRelativePath(data.assets)
    props.LIBS_FOLDER_RELATIVE_PATH = getRelativePath(data.libs)
    props.APK_PATH = getRelativePath(data.apk)
    props.LIBRARY_PROJECT = data.isLibrary
    props.myProGuardCfgFiles = new util.ArrayList[String]()

    if (data.proguardConfig.nonEmpty) {
      val proguardFile = new File(module.getProject.getBasePath) / "proguard-sbt.txt"
      FileUtil.writeToFile(proguardFile, data.proguardConfig.mkString(SystemProperties.getLineSeparator))
      props.myProGuardCfgFiles.add(proguardFile.getCanonicalPath)
      props.RUN_PROGUARD = true
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

