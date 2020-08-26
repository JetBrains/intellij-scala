package org.jetbrains.plugins.scala
package project
package external

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

import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
abstract class AbstractDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  def createImporter(toImport: Seq[DataNode[E]],
                     projectData: ProjectData,
                     project: Project,
                     modelsProvider: IdeModifiableModelsProvider): Importer[E]

  override def getTargetDataKey: Key[E] = key

  override final def importData(toImport: util.Collection[DataNode[E]],
                                projectData: ProjectData,
                                project: Project,
                                modelsProvider: IdeModifiableModelsProvider): Unit =
    createImporter(toImport.asScala.toSeq, projectData, project, modelsProvider).importData()
}

// TODO The Importer trait is probably redundant
/**
 * The purposes of this trait are the following:
 *    - Encapsulate logic necessary for importing specified data
 *    - Wrap "unsafe" methods from IdeModifiableModelsProvider
 *    - Collect import parameters as class fields to eliminate necessity of
 *      dragging them into each and every method of ProjectDataService
 *    - Abstract from External System's API which is rather unstable
 */
trait Importer[E] {
  // TODO abstract vals in traits is an anti-pattern, may use constructor parameters instead
  protected val dataToImport: Seq[DataNode[E]]
  protected val projectData: ProjectData
  protected val project: Project
  protected val modelsProvider: IdeModifiableModelsProvider

  def importData(): Unit

  // IdeModifiableModelsProvider wrappers

  // TODO can be extension methods for IdeModifiableModelsProvider
  protected def findIdeModule(name: String): Option[Module] =
    Option(modelsProvider.findIdeModule(name))

  protected def findIdeModule(data: ModuleData): Option[Module] =
    Option(modelsProvider.findIdeModule(data))

  protected def getModifiableFacetModel(module: Module): ModifiableFacetModel =
    modelsProvider.getModifiableFacetModel(module)

  protected def getModifiableLibraryModel(library: Library): Library.ModifiableModel =
    modelsProvider.getModifiableLibraryModel(library)

  protected def getModifiableRootModel(module: Module): ModifiableRootModel =
    modelsProvider.getModifiableRootModel(module)

  protected def getModules: Array[Module] =
    modelsProvider.getModules

  // Utility methods

  protected def getIdeModuleByNode(node: DataNode[_]): Option[Module] =
    for {
      moduleData <- Option(node.getData(ProjectKeys.MODULE))
      module <- findIdeModule(moduleData)
    } yield module

  // TODO Can be a "static" utility method
  protected def executeProjectChangeAction(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  // TODO "set" is for setters, but the method modifies the IDEA's model
  protected def setScalaSdk(library: Library,
                  compilerClasspath: collection.Seq[File])
                 (maybeVersion: Option[String] = library.compilerVersion): Unit =
    Importer.setScalaSdk(modelsProvider, library, ScalaLibraryProperties(maybeVersion, compilerClasspath))
}

object Importer {

  def setScalaSdk(modelsProvider: IdeModifiableModelsProvider,
                  library: Library,
                  properties: ScalaLibraryProperties): Unit =
    modelsProvider.getModifiableLibraryModel(library) match { // FIXME: should be implemented in External System
      case modelEx: LibraryEx.ModifiableModelEx =>
        modelEx.setKind(ScalaLibraryType.Kind)
        modelEx.setProperties(properties)
    }
}

abstract class AbstractImporter[E](override val dataToImport: Seq[DataNode[E]],
                                   override val projectData: ProjectData,
                                   override val project: Project,
                                   override val modelsProvider: IdeModifiableModelsProvider) extends Importer[E]
