package org.jetbrains.sbt.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager, OrderRootType}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.project.SbtProjectUtil.{cachedModuleForPsiFile, isInSbtProject}

import java.util.concurrent.ConcurrentHashMap

@Service(Array(Service.Level.PROJECT))
private[sbt] final class SbtProjectImportStateService(project: Project) {
  import SbtProjectImportStateService.State

  private val state: ConcurrentHashMap[String, State] = new ConcurrentHashMap()

  /**
   * Checks if the project the provided file is in is imported as an sbt project using the
   * native sbt build tool support.
   *
   * @param file the file whose project will be checked if it is an sbt project
   * @return `true` if the file is in an sbt project imported using the native sbt build tool support and the project
   *         is fully imported. `true` if the project is not handled by the native sbt build tool support.
   *         `false` or the module is not linked to an external system project and not handled by any other build tool.
   */
  def isImported(file: PsiFile): Boolean = {
    cachedModuleForPsiFile(file) match {
      case Some(m) if isInSbtProject(m) =>
        val relevantPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(m))
        relevantPath match {
          case Some(path) =>
            val s = state.computeIfAbsent(path, computeImportState)
            s == State.Imported
          case None =>
            // Cannot find a module linked to an external system project, and it is not handled by another build tool,
            // assume the project is not imported.
            false
        }
      case Some(_) =>
        // Handled by some other build tool.
        true
      case None =>
        // Cannot find a module for the file, do not assume anything about the project.
        true
    }
  }

  def reset(): Unit = {
    state.clear()
  }

  private def computeImportState(relevantProjectPath: String): State = {
    val allBuildModules = ModuleManager.getInstance(project).getModules.filter(_.hasBuildModuleType)
    val relevantBuildModules = allBuildModules.filter { module =>
      val projectRootPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
      projectRootPath.contains(relevantProjectPath)
    }
    val imported = relevantBuildModules.nonEmpty && relevantBuildModules.forall(hasSbtLibrary)
    val newState = if (imported) State.Imported else State.NotImported
    newState
  }

  private def hasSbtLibrary(buildModule: Module): Boolean = {
    val dependencies = ModuleRootManager.getInstance(buildModule).getOrderEntries
    val sbtLibrary = dependencies.iterator.collectFirst {
      case library: LibraryOrderEntry if isSbtLibrary(library) => library
    }
    sbtLibrary.isDefined
  }

  private def isSbtLibrary(library: LibraryOrderEntry): Boolean = {
    def expectedName = Option(library.getLibraryName).exists(_.startsWith(s"sbt: sbt-"))
    def expectedJars = library.getRootFiles(OrderRootType.CLASSES).nonEmpty
    expectedName && expectedJars
  }
}

private[sbt] object SbtProjectImportStateService {

  def instance(project: Project): SbtProjectImportStateService =
    project.getService(classOf[SbtProjectImportStateService])

  private sealed trait State extends Product with Serializable
  private object State {
    case object Imported extends State
    case object NotImported extends State
  }
}
