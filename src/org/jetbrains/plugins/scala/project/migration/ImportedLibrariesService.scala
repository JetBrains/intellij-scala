package org.jetbrains.plugins.scala.project.migration

import java.io.File
import java.net.URLClassLoader
import java.util

import com.intellij.openapi.externalSystem.model.project.{LibraryData, LibraryDependencyData, LibraryPathType, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import org.jetbrains.plugins.scala.codeInspection.bundled.{BundledInspectionBase, BundledInspectionStoreComponent}
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport, SettingsDescriptor}
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.project.migration.handlers.{ArtifactHandlerComponent, ScalaLibraryMigrationHandler, VersionedArtifactHandlerBase}
import org.jetbrains.plugins.scala.project.migration.store.ManifestAttributes.ManifestAttribute
import org.jetbrains.plugins.scala.project.migration.store.{ManifestAttributes, SerializationUtil}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 25.07.16.
  */
class ImportedLibrariesService extends AbstractProjectDataService[LibraryDependencyData, LibraryOrderEntry] {
  private def isEnabled(project: Project) = ScalaProjectSettings.getInstance(project).isBundledMigratorsSearchEnabled
  
  override def getTargetDataKey: Key[LibraryDependencyData] = ProjectKeys.LIBRARY_DEPENDENCY

  override def importData(toImport: util.Collection[DataNode[LibraryDependencyData]], 
                          projectData: ProjectData, project: Project, 
                          modelsProvider: IdeModifiableModelsProvider): Unit = {
    if (!isEnabled(project)) return 
    
    import ImportedLibrariesService.{loadClassFromJar, showWarning}
    
    val it = toImport.iterator()
    val service = getMigrationApi(project)

    val inspectionStoreComponent = BundledInspectionStoreComponent getInstance project
    inspectionStoreComponent.clearLoaded()

    val handlerComponent = ArtifactHandlerComponent.getInstance(project)
    val foundMigrators = mutable.HashSet[ScalaLibraryMigrator]()
    
    def processFoundToHandlers(toHandlers: Iterable[ScalaLibraryMigrationHandler], target: LibraryData) {
      modelsProvider.getAllLibraries foreach { //todo performance ?   
        case lib if lib.getName != target.getInternalName => //todo better comparison? 
          val foundHandlers = toHandlers.filter(_.acceptsFrom(lib))

          val filteredHandlers = foundHandlers.foldLeft(mutable.HashSet[ScalaLibraryMigrationHandler]()) {
            case (set, handler) => if (set.exists(_.precede(handler))) set else {set.add(handler); set}
          }

          foundMigrators ++= filteredHandlers.flatMap(_.getMigrators(lib, target))
        case _ =>
      }
    }
    
    while (it.hasNext) {
      val data = it.next().getData
      val target = data.getTarget
      
      val predefinedHandlersForTarget = handlerComponent.getAllForTo(target)
      
      if (predefinedHandlersForTarget.nonEmpty) processFoundToHandlers(predefinedHandlersForTarget, target) else {
        val i = target.getPaths(LibraryPathType.BINARY).iterator()
        var bundledFound = Seq.empty[(ManifestAttribute, String)]
        var pathFound = ""
        
        while (i.hasNext && bundledFound.isEmpty) {
          val p = i.next()
          val f = SerializationUtil.discoverIn(p)
          
          if (f.nonEmpty) {
            bundledFound = f
            pathFound = SerializationUtil.stripPath(p)
          }
        }
        
        if (bundledFound.nonEmpty) {
          bundledFound foreach {
            case (ManifestAttributes.MigratorFqnAttribute, fqn) =>
              val loaded = loadClassFromJar(service, pathFound, Seq(fqn), a => a.asInstanceOf[ScalaLibraryMigrationHandler])
              if (loaded.nonEmpty) processFoundToHandlers(loaded, target)
            case (ManifestAttributes.InspectionPackageAttribute, fqn) =>
              val loaded = loadClassFromJar(service, pathFound, Seq(fqn), a => a.asInstanceOf[BundledInspectionBase])
              if (loaded.nonEmpty) loaded.foreach(l => inspectionStoreComponent.addLoadedInspection(pathFound, fqn, l))
            case (a, v) => showWarning(service, s"Unknown attribute: ${a.name} : $v")
          }
        }
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
              
              new ScalaMigrationRunner(project).runMigrators(choosen.toSeq, service)
            }, ())
        case "close" =>
        case _ => 
      })
    
    inspectionStoreComponent.completeLoading()
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

  def showWarning(service: MigrationApiService, txt: String) = 
    service.showReport(MigrationReport.createSingleMessageReport(MigrationReport.Warning, txt))
  
  def showExceptionWarning(service: MigrationApiService, ex: Throwable) = showWarning(service, ex.getMessage +
    "\n" + ex.getStackTrace.take(STACKTRACE_FROM_REPORT_CUT_SIZE).mkString("\n"))
  
  
  def loadClassFromJar[T](service: MigrationApiService, filePath: String, fqns: Iterable[String], 
                          converter: Any => T): Iterable[T] = {
    val file = new File(filePath)
    if (!file.exists()) return Seq.empty

    val myClassLoader = new URLClassLoader(Array(file.toURI.toURL), classOf[VersionedArtifactHandlerBase].getClassLoader)
    
    fqns.flatMap{
      fqn => Try(myClassLoader.loadClass(fqn)) flatMap (
          cs => Try(cs.newInstance())
        ) flatMap (ins => Try(converter(ins))) match {
        case Success(v) => 
          Some(v)
        case Failure(_: ClassNotFoundException) =>
          showWarning(service, s"Cannot find class $fqn declared in ${file.getName}")
          None
        case Failure(_: ClassCastException) => 
          showWarning(service, s"Cannot cast to the proper type class $fqn declared in ${file.getName}")
          None
        case Failure(e: InstantiationException) => 
          showWarning(service, s"Cannot instantiate class $fqn declared in ${file.getName}")
          None
        case Failure(e: Throwable) =>
          showExceptionWarning(service, e)
          None
      }
    }
  }
}