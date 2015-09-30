package org.jetbrains.sbt
package project.data

import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, ExternalSystemException, DataNode}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import org.jetbrains.plugins.scala.project._
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaSdkDataService(platformFacade: PlatformFacade, val helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaSdkData, Library](ScalaSdkData.Key)
  with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[ScalaSdkData]], project: Project) {
    toImport.asScala.foreach(doImport(_, project))
  }

  private def doImport(sdkNode: DataNode[ScalaSdkData], project: Project) {
    for {
      module <- getIdeModuleByNode(sdkNode, project)
      sdkData = sdkNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("SBT", sdkData.compilerOptions)
      val compilerVersion = sdkData.scalaVersion
      val scalaLibraries = project.libraries.filter(_.getName.contains("scala-library"))
      if (scalaLibraries.isEmpty)
        return

      // TODO Why SBT's scala-libary module version sometimes differs from SBT's declared scalaVersion?
      val scalaLibrary = scalaLibraries
        .find(_.scalaVersion == Some(compilerVersion))
        .orElse(scalaLibraries.find(_.scalaVersion.exists(_.toLanguageLevel == compilerVersion.toLanguageLevel)))
        .getOrElse(throw new ExternalSystemException("Cannot find project Scala library " +
          compilerVersion.number + " for module " + sdkNode.getData(ProjectKeys.MODULE).getExternalName))

      if (!scalaLibrary.isScalaSdk) {
        val languageLevel = compilerVersion.toLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
        val compilerClasspath = sdkData.compilerClasspath
        scalaLibrary.convertToScalaSdkWith(languageLevel, compilerClasspath)
      }
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {

  }
}
