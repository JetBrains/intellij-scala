package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.CompilationUnitId
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.project.settings.DisplayModuleName

/**
 * A build target: the module that is actually compiled, together with the source scope whose output the
 * compilers read.
 */
final case class ModuleKey(module: Module, sourceScope: SourceScope)

object ModuleKey {

  /**
   * The build target `virtualFile` belongs to, or `None` when it belongs to no module.
   */
  def of(project: Project, virtualFile: VirtualFile): Option[ModuleKey] = inReadAction {
    Option(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
      .map(_.findRepresentativeModuleForSharedSourceModuleOrSelf)
      .map(ModuleKey(_, FileCompilationScope.sourceScopeOf(project, virtualFile)))
  }

  /** The build target of an already resolved compilation scope. */
  def of(scope: FileCompilationScope): ModuleKey =
    ModuleKey(scope.module.findRepresentativeModuleForSharedSourceModuleOrSelf, scope.sourceScope)

  /**
   * The build target a compiler reported having built.
   *
   * A build names the module by its sbt display name where the project has unique ones and by its IntelliJ
   * name otherwise, and the two differ for every module of a multi-build sbt project but the root, whose
   * module names carry the root project prefix that display names lack. Both are tried for that reason.
   * `None` when neither resolves.
   */
  def of(project: Project, unitId: CompilationUnitId): Option[ModuleKey] = inReadAction {
    val manager = ModuleManager.getInstance(project)
    manager.getModules
      .find(DisplayModuleName.getInstance(_).name == unitId.moduleId)
      .orElse(Option(manager.findModuleByName(unitId.moduleId)))
      .map(_.findRepresentativeModuleForSharedSourceModuleOrSelf)
      .map(ModuleKey(_, if (unitId.testScope) SourceScope.Test else SourceScope.Production))
  }
}
