package org.jetbrains.sbt
package project.data

import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.project.Project
import collection.JavaConverters._
import com.intellij.openapi.roots.libraries.Library
import java.io.File
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import org.jetbrains.plugins.scala.configuration._
import SbtSdkDataService._

/**
 * @author Pavel Fatin
 */
class SbtSdkDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaSdkData, Library](ScalaSdkData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaSdkData]], project: Project) {
    toImport.asScala.foreach { sdkNode =>
      val module = {
        val moduleName = sdkNode.getData(ProjectKeys.MODULE).getName
        helper.findIdeModule(moduleName, project)
      }

      val sdkData = sdkNode.getData

      val compilerClasspath = sdkData.compilerClasspath.toSet
//      val compilerOptions = sdkData.compilerOptions // TODO

      val scalaSdk = findScalaSdkIn(project, compilerClasspath).orElse {
        val standardLibrary = findScalaStandardLibraryIn(project, compilerClasspath)
        standardLibrary.map(_.convertToScalaSdkWith(compilerClasspath.toSeq))
      }

      scalaSdk.foreach { properSdk =>
        val existingScalaSdk = module.scalaSdk

        existingScalaSdk match {
          case None =>
            module.attach(properSdk)
          case Some(sdk) if sdk.library != properSdk.library =>
            module.detach(sdk)
            module.attach(properSdk)
          case _ =>
        }
      }
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {

  }
}

object SbtSdkDataService {
  def findScalaSdkIn(project: Project, compilerClasspath: Set[File]): Option[ScalaSdk] =
    project.scalaSdks.find(_.compilerClasspath.toSet == compilerClasspath)

  def findScalaStandardLibraryIn(project: Project, compilerClasspath: Set[File]): Option[Library] = {
    val compilerStandardLibraryFile = compilerClasspath.find(_.getName.startsWith("scala-library"))

    compilerStandardLibraryFile.flatMap(file => project.libraries.find(_.classes.contains(file)))
  }
}
