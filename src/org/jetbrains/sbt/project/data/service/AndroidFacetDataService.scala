package org.jetbrains.sbt
package project.data.service

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.android.facet.{AndroidFacet, AndroidFacetType, AndroidRootUtil}
import org.jetbrains.sbt.project.data.AndroidFacetData

/**
 * @author Nikolay Obedin
 * @since 8/12/14.
 */
class AndroidFacetDataService extends AbstractDataService[AndroidFacetData, AndroidFacet](AndroidFacetData.Key) {

  override def createImporter(toImport: Seq[DataNode[AndroidFacetData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[AndroidFacetData] =
    new AndroidFacetDataService.Importer(toImport, projectData, project, modelsProvider)
}

object AndroidFacetDataService {
  private class Importer(dataToImport: Seq[DataNode[AndroidFacetData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
      extends AbstractImporter[AndroidFacetData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach { facetNode =>
        for {
          module <- getIdeModuleByNode(facetNode)
          facet = getOrCreateFacet(module)
        } {
          configureFacet(module, facet, facetNode.getData)
        }
      }

    private def getOrCreateFacet(module: Module): AndroidFacet =
      Option(getModifiableFacetModel(module).getFacetByType(AndroidFacet.ID)).getOrElse(createFacet(module))

    private def createFacet(module: Module): AndroidFacet = {
      val model = getModifiableFacetModel(module)
      val facetType = new AndroidFacetType
      val facet = facetType.createFacet(module, "Android", facetType.createDefaultConfiguration(), null)
      model.addFacet(facet)
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
  }
}

