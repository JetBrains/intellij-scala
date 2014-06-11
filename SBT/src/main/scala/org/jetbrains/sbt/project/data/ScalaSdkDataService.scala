package org.jetbrains.sbt
package project.data

import java.util
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import org.jetbrains.plugins.scala.configuration._
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaSdkDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaSdkData, Library](ScalaSdkData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaSdkData]], project: Project) {
    toImport.asScala.foreach(doImport(_, project))
  }

  private def doImport(sdkNode: DataNode[ScalaSdkData], project: Project) {
    val sdkData = sdkNode.getData

    val compilerOptions = sdkData.compilerOptions // TODO

    val compilerVersion = sdkData.scalaVersion

    val matchedLibrary = project.libraries.find(_.scalaVersion == Some(compilerVersion))

    for (library <- matchedLibrary if !library.isScalaSdk) {
      val languageLevel = ScalaLanguageLevel.from(compilerVersion, true)
      val compilerClasspath = sdkData.compilerClasspath
      library.convertToScalaSdkWith(languageLevel, compilerClasspath)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {

  }
}
