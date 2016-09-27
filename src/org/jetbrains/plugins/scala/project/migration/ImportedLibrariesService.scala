package org.jetbrains.plugins.scala.project.migration

import java.util

import com.intellij.openapi.externalSystem.model.project.{LibraryDependencyData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, SettingsDescriptor}
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.project.migration.handlers.{ArtifactHandlerComponent, ScalaLibraryMigrationHandler}

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 25.07.16.
  */
class ImportedLibrariesService extends AbstractProjectDataService[LibraryDependencyData, LibraryOrderEntry] {
  private def isEnabled = false
  
  override def getTargetDataKey: Key[LibraryDependencyData] = ProjectKeys.LIBRARY_DEPENDENCY

  override def importData(toImport: util.Collection[DataNode[LibraryDependencyData]], 
                          projectData: ProjectData, project: Project, 
                          modelsProvider: IdeModifiableModelsProvider): Unit = {
    if (!isEnabled) return 
    
    val it = toImport.iterator()
    val service = getMigrationApi(project)

    val handlerComponent = ArtifactHandlerComponent.getInstance(project)
    val foundMigrators = mutable.HashSet[ScalaLibraryMigrator]()
    //val oldLibraries = modelsProvider.getAllLibraries
    
    while (it.hasNext) {
      val data = it.next().getData
      val target = data.getTarget
      val handlersForTarget = handlerComponent.getAllForTo(target)
      
      if (handlersForTarget.nonEmpty) modelsProvider.getAllLibraries foreach { //todo performance ?   
        case lib if lib.getName != target.getInternalName => //to do better comparison? 
          val foundHandlers = handlersForTarget.filter(_.acceptsFrom(lib))
          
          val filteredHandlers = foundHandlers.foldLeft(mutable.HashSet[ScalaLibraryMigrationHandler]()) {
            case (set, handler) => if (set.exists(_.precede(handler))) set else {set.add(handler); set}
          }

          foundMigrators ++= filteredHandlers.flatMap(_.getMigrators(lib, target))
        case _ => 
      }
    }
    
    
    if (foundMigrators.nonEmpty) service.showPopup(ImportedLibrariesService.MIGRATORS_FOUND_MESSAGE,
      "Dependency migrators found", {
        case "show" => 
          service.showDialog("Azaza", "Migrators found",  
            SettingsDescriptor(foundMigrators.map(m => (m.getName, true)), Iterable.empty, Iterable.empty), 
            (descriptor: SettingsDescriptor) => {
              val choosen = descriptor.checkBoxes.zip(foundMigrators).collect {
                case ((_, isSelected), migrator) if isSelected => migrator
              }
              
              new ScalaMigrationRunner().runMigrators(choosen.toSeq, service)
            }, ())
        case "close" =>
        case _ => 
      })
  }
  
  private def getMigrationApi(project: Project): MigrationApiService = MigrationApiImpl.getApiInstance(project)
}

object ImportedLibrariesService {
  val STACKTRACE_FROM_REPORT_CUT_SIZE = 8
  
  val MIGRATORS_FOUND_MESSAGE =
    """
      |<html>
      |<body>
      |<p>Some migrators has been found.</p>
      |<br>
      |<a href="ftp://show">Show</a>  <a href="ftp://close">Close</a>
      |</body>
      |</html>
    """.stripMargin
}