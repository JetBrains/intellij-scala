package org.jetbrains.sbt
package project.data

import java.util
import java.util.regex.Pattern

import com.intellij.openapi.externalSystem.model.{Key, DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.{ProjectData, ModuleData, LibraryDependencyData}
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemConstants, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{OrderEntry, ModuleRootManager, LibraryOrderEntry}

import collection.JavaConversions._

object IntraProjectDependencyDataService {
  val DependencyCleanupStageKey: Key[LibraryDependencyData] = new Key(classOf[LibraryDependencyData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  //A simple model for an external dependency - used to help find candidates for
  // intra-project linking to an existing module.
  private case class ModuleId(group: String, name: String, version: String)

  private object ModuleId {

    private val MavenCoordinatePattern = Pattern.compile("SBT: ([^: ]+):([^: ]+):([^: ]+)")

    //this is kind of ugly
    def fromString(coordinates: String): Option[ModuleId] = {
      val matcher = MavenCoordinatePattern.matcher(coordinates)
      if(!matcher.matches()) {
        None
      } else {
        //strip off a version suffix (if present) from the artifact
        // The scala standard is to postfix the name with the binary version of Scala used
        // Modules that have been imported into IDEA, however, do not include the cross version
        // suffix on their names. The right way to do this is to have the sbt-structure plugin
        // emit the fully cross-versioned name of the main artifact from the project, and we could then
        // match on that.
        //
        //This is just a (rather dumb) heuristic
        val name = matcher.group(2)
        val underscore = name.lastIndexOf('_')
        val realName = if(underscore != -1) {
          name.substring(0, underscore)
        } else {
          name
        }
        val group = matcher.group(1)
        val version = matcher.group(3)

        Some(ModuleId(group, realName, version))
      }
    }


    def fromIdeModule(module: Module): Option[ModuleId] = {
      //TODO: Figure out a way to determine if a module is a Maven module
      val moduleName = module.getName
      for {
        moduleGroup <- Option(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_GROUP_KEY)).map(_.trim).filter(!_.isEmpty)
        moduleVersion <- Option(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_VERSION_KEY)).map(_.trim).filter(!_.isEmpty)
      } yield {
        ModuleId(moduleGroup, moduleName, moduleVersion)
      }
    }

  }


}



class IntraProjectDependencyDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
        extends AbstractDataService[LibraryDependencyData, LibraryOrderEntry](IntraProjectDependencyDataService.DependencyCleanupStageKey) {

  import IntraProjectDependencyDataService._

  override def doImportData(toImport: util.Collection[DataNode[LibraryDependencyData]], project: Project): Unit = {

    //Attempt to find a pre-existing module in the workspace that may match an external library
    // dependency contained in the import. If a candidate is found,

    //find all the library references that may be fulfilled by existing modules in the project
    val candidateModules: Map[ModuleId, Module] = platformFacade.getModules(project).flatMap { ideModule =>
      ModuleId.fromIdeModule(ideModule).map(moduleId => moduleId -> ideModule)
    }(collection.breakOut)

    toImport.groupBy(_.getDataNode(ProjectKeys.MODULE)).foreach {
      case (moduleDataNode, depDataNodes) =>
        Option(helper.findIdeModule(moduleDataNode.getData, project)).foreach { module =>
          val moduleRootManager = ModuleRootManager.getInstance(module)
          val moduleRootModel = moduleRootManager.getModifiableModel
          try {
            //if we find a match, remove the library entry (since it was created by the previous step)
            // but only add a new module dependency if it's actually detected in the project
            moduleRootModel.getOrderEntries.foreach {
              case libraryEntry: LibraryOrderEntry =>
                ModuleId.fromString(libraryEntry.getLibraryName).foreach { moduleId =>
                  candidateModules.get(moduleId).foreach { targetModule =>
                    val scope = libraryEntry.getScope
                    val isExported = libraryEntry.isExported
                    moduleRootModel.removeOrderEntry(libraryEntry)
                    if(!moduleRootModel.getModuleDependencies.contains(targetModule)) {
                      val moduleEntry = moduleRootModel.addModuleOrderEntry(targetModule)
                      moduleEntry.setScope(scope)
                      moduleEntry.setExported(isExported)
                    }
                  }
                }
              case _ =>
                //do nothing
            }
          } finally {
            moduleRootModel.commit()
          }
        }
    }
  }

  override def doRemoveData(toRemove: util.Collection[_ <: LibraryOrderEntry], project: Project): Unit = { }
}

