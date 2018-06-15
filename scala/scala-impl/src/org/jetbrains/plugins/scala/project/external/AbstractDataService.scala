package org.jetbrains.plugins.scala.project.external

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
import org.jetbrains.plugins.scala.project.{Platform, ScalaLanguageLevel, ScalaLibraryProperties, ScalaLibraryType}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
abstract class AbstractDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  def createImporter(toImport: Seq[DataNode[E]],
                     projectData: ProjectData,
                     project: Project,
                     modelsProvider: IdeModifiableModelsProvider): Importer[E]

  def getTargetDataKey: Key[E] = key

  override final def importData(toImport: util.Collection[DataNode[E]],
                                projectData: ProjectData,
                                project: Project,
                                modelsProvider: IdeModifiableModelsProvider): Unit =
    createImporter(toImport.asScala.toSeq, projectData, project, modelsProvider).importData()
}

/**
 * The purposes of this trait are the following:
 *    - Encapsulate logic necessary for importing specified data
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

  def executeProjectChangeAction(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  def setScalaSdk(library: Library,
                  platform: Platform,
                  languageLevel: ScalaLanguageLevel,
                  compilerClasspath: Seq[File]): Unit = {

    val properties = new ScalaLibraryProperties()
    properties.platform = platform
    properties.languageLevel = languageLevel
    properties.compilerClasspath = compilerClasspath

    val model = getModifiableLibraryModelEx(library)
    model.setKind(ScalaLibraryType.instance.getKind)
    model.setProperties(properties)

  }
}

abstract class AbstractImporter[E](val dataToImport: Seq[DataNode[E]],
                                   val projectData: ProjectData,
                                   val project: Project,
                                   val modelsProvider: IdeModifiableModelsProvider) extends Importer[E]
