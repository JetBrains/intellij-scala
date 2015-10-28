package org.jetbrains.sbt.project.data.service

import java.io.File
import java.util

import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.CommonProcessors.CollectProcessor
import org.jetbrains.plugins.scala.project.{ScalaLibraryType, ScalaLanguageLevel, ScalaSdk, ScalaLibraryProperties, ScalaLibraryName}

import scala.collection.JavaConversions._

/**
 * @author Pavel Fatin
 */
abstract class AbstractDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  def createImporter(toImport: Seq[DataNode[E]],
                     projectData: ProjectData,
                     project: Project,
                     modelsProvider: IdeModifiableModelsProvider): Importer[E]

  def getTargetDataKey = key

  override final def importData(toImport: util.Collection[DataNode[E]],
                                projectData: ProjectData,
                                project: Project,
                                modelsProvider: IdeModifiableModelsProvider): Unit =
    createImporter(toImport.toSeq, projectData, project, modelsProvider).importData()
}

/**
 * The purposes of this trait are the following:
 *    - Incapsulate logic necessary for importing specified data
 *    - Wrap "unsafe" methods from IdeModifiableModelsProvider
 *    - Collect import parameters as class fields to eliminate necessity of
 *      dragging them into each and every method of ProjectDataService
 *    - Abstract from External System's API which is rather unstable
 */
trait Importer[E] {
  val dataToImport: Seq[DataNode[E]]
  val projectData: ProjectData
  val project: Project
  val modelsProvider: IdeModifiableModelsProvider

  def importData(): Unit

  // IdeModifiableModelsProvider wrappers

  def findIdeModule(name: String): Option[Module] =
    Option(modelsProvider.findIdeModule(name))

  def findIdeModule(data: ModuleData): Option[Module] =
    Option(modelsProvider.findIdeModule(data))

  def getModifiableFacetModel(module: Module): ModifiableFacetModel =
    modelsProvider.getModifiableFacetModel(module)

  def getModifiableLibraryModel(library: Library): Library.ModifiableModel =
    modelsProvider.getModifiableLibraryModel(library)

  def getModifiableRootModel(module: Module): ModifiableRootModel =
    modelsProvider.getModifiableRootModel(module)

  def getModules: Array[Module] =
    modelsProvider.getModules

  // FIXME: should be implemented in External System
  def getModifiableLibraryModelEx(library: Library): LibraryEx.ModifiableModelEx =
    modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]

  // Utility methods

  def getIdeModuleByNode(node: DataNode[_]): Option[Module] =
    for {
      moduleData <- Option(node.getData(ProjectKeys.MODULE))
      module <- findIdeModule(moduleData)
    } yield module

  def getScalaLibraries: Set[Library] =
    modelsProvider.getAllLibraries.filter(l => Option(l.getName).exists(_.contains(ScalaLibraryName))).toSet

  def getScalaLibraries(module: Module): Set[Library] = {
    val collector = new CollectProcessor[Library]()
    getModifiableRootModel(module).orderEntries().librariesOnly().forEachLibrary(collector)
    collector.getResults.toSet.filter(l => Option(l.getName).exists(_.contains(ScalaLibraryName)))
  }

  def executeProjectChangeAction(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  def convertToScalaSdk(library: Library, languageLevel: ScalaLanguageLevel, compilerClasspath: Seq[File]): ScalaSdk = {
    val properties = new ScalaLibraryProperties()
    properties.languageLevel = languageLevel
    properties.compilerClasspath = compilerClasspath

    val model = getModifiableLibraryModelEx(library)
    model.setKind(ScalaLibraryType.instance.getKind)
    model.setProperties(properties)

    new ScalaSdk(library)
  }
}

abstract class AbstractImporter[E](val dataToImport: Seq[DataNode[E]],
                                   val projectData: ProjectData,
                                   val project: Project,
                                   val modelsProvider: IdeModifiableModelsProvider) extends Importer[E]
