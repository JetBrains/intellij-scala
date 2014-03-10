package org.jetbrains.plugins.scala
package config

import java.util
import java.io.File
import collection.JavaConverters._
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.project.Project
import com.intellij.facet.FacetManager
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore}
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.scala.config.FileAPI._
import ScalaGradleDataService._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
        extends AbstractDataService[ScalaModelData, ScalaFacet](ScalaModelData.KEY) {

  def doImportData(toImport: util.Collection[DataNode[ScalaModelData]], project: Project) {
    toImport.asScala.foreach { facetNode =>
      val module = {
        val moduleName = facetNode.getData(ProjectKeys.MODULE).getName
        helper.findIdeModule(moduleName, project)
      }

      val scalaData = facetNode.getData

      val compilerLibrary = {
        val classpath = scalaData.getScalaClasspath.asScala.toSet

        findLibraryByClassesIn(project)(classpath).getOrElse(
          createLibraryIn(project)(compilerLibraryNameFor(classpath), classpath))
      }

      val compilerOptions = compilerOptionsFrom(scalaData)

      def setup(facet: ScalaFacet) {
        facet.compilerLibraryId = LibraryId(compilerLibrary.getName, LibraryLevel.Project)
        facet.compilerParameters = compilerOptions.toArray
      }

      ScalaFacet.findIn(module).map(setup(_)).getOrElse(
        ScalaFacet.createIn(module)(setup(_)))
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: ScalaFacet], project: Project) {
    toRemove.asScala.foreach(delete(_))
  }
}

object ScalaGradleDataService {
  def findLibraryByClassesIn(project: Project)(classpath: Set[File]): Option[Library] =
    ProjectLibraryTable.getInstance(project).getLibraries.find(has(classpath))

  private def has(classpath: Set[File])(library: Library) =
    library.getFiles(OrderRootType.CLASSES).toSet.map(VfsUtilCore.virtualToIoFile) == classpath

  def compilerLibraryNameFor(classpath: Set[File]): String = {
    val compilerVersion = classpath.find(_.getName.startsWith("scala-compiler"))
            .flatMap(readProperty(_, "compiler.properties", "version.number"))

    "Gradle: scala-compiler" + compilerVersion.fold("")("-" + _)
  }

  def createLibraryIn(project: Project)(name: String, classpath: Set[File]): Library = {
    val library = ProjectLibraryTable.getInstance(project).createLibrary()
    val model = library.getModifiableModel
    model.setName(name)
    classpath.foreach(file => model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES))
    model.commit()
    library
  }

  def configure(compilerLibraryName: String, compilerOptions: Seq[String])(facet: ScalaFacet) {
    facet.compilerLibraryId = LibraryId(compilerLibraryName, LibraryLevel.Project)
    facet.compilerParameters = compilerOptions.toArray
  }

  def delete(facet: ScalaFacet) {
    val facetManager = FacetManager.getInstance(facet.getModule)
    val model = facetManager.createModifiableModel
    model.removeFacet(facet)
    model.commit()
  }

  def compilerOptionsFrom(data: ScalaModelData): Seq[String] = {
    val options = data.getScalaCompileOptions

    val presentations = Seq(
      options.isDeprecation -> "-deprecation",
      options.isUnchecked -> "-unchecked",
      options.isOptimize -> "-optimise",
      !isEmpty(options.getDebugLevel) -> s"-g:${options.getDebugLevel}",
      !isEmpty(options.getEncoding) -> s"-encoding ${options.getEncoding}",
      !isEmpty(data.getTargetCompatibility) -> s"-target:jvm-${data.getTargetCompatibility}")

    presentations.flatMap((include _).tupled)
  }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty
}
