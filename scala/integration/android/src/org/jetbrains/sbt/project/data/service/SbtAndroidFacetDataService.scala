package org.jetbrains.sbt
package project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.facet.{AndroidFacet, AndroidFacetType, AndroidRootUtil}
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.data.SbtAndroidFacetData

import java.io.File
import java.util
import scala.jdk.CollectionConverters._

class SbtAndroidFacetDataService extends ScalaAbstractProjectDataService[SbtAndroidFacetData, AndroidFacet](SbtAndroidFacetData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtAndroidFacetData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit =
    toImport.forEach { facetNode =>
      for {
        module <- modelsProvider.getIdeModuleByNode(facetNode)
        facet = getOrCreateFacet(module, modelsProvider)
      } {
        configureFacet(module, facet, facetNode.getData)
      }
    }

  private def getOrCreateFacet(module: Module, modelsProvider: IdeModifiableModelsProvider): AndroidFacet = {
    val existingFacet = Option(modelsProvider.getModifiableFacetModel(module).getFacetByType(AndroidFacet.ID))
    existingFacet.getOrElse(createFacet(module, modelsProvider))
  }

  private def createFacet(module: Module, modelsProvider: IdeModifiableModelsProvider): AndroidFacet = {
    val model = modelsProvider.getModifiableFacetModel(module)
    val facetType = new AndroidFacetType
    val facet = facetType.createFacet(module, "Android", facetType.createDefaultConfiguration(), null)
    model.addFacet(facet)
    facet
  }

  private def configureFacet(module: Module, facet: AndroidFacet, data: SbtAndroidFacetData): Unit = {
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
    configuration.myProGuardCfgFiles = new util.ArrayList[String]()

    if (!data.proguardConfig.isEmpty) {
      val proguardFile = new File(module.getProject.getBasePath) / "proguard-sbt.txt"
      FileUtil.writeToFile(proguardFile, data.proguardConfig.asScala.mkString(System.lineSeparator()))
      configuration.myProGuardCfgFiles.add(proguardFile.getCanonicalPath)
      configuration.RUN_PROGUARD = true
    }
  }
}

