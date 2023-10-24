package org.jetbrains.plugins.scala.compiler.references

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import org.jetbrains.plugins.scala.indices.protocol.sbt.{Configuration, SbtCompilationInfo}

private class ScalaDirtyScopeHolder(
  project:             Project,
  fileTypes:           Array[FileType],
  fileIndex:           ProjectFileIndex,
  fileDocManager:      FileDocumentManager,
  psiDocManager:       PsiDocumentManager,
  modificationTracker: ModificationTracker,
) extends DirtyScopeHolder[ScalaDirtyScopeHolder.ScopedModule](
      project,
      fileTypes,
      fileIndex,
      fileDocManager,
      psiDocManager,
      modificationTracker
    ) {
  import ScalaDirtyScopeHolder._

  override protected def scopeForSourceContentFile(vFile: VirtualFile): Option[ScopedModule] = {
    val fType = fileTypeRegistry.getFileTypeByFileName(vFile.getNameSequence)

    inReadAction {
      if (fileTypes.contains(fType) && fileIndex.isInSourceContent(vFile)) {
        fileIndex.getModuleForFile(vFile).toOption.map { module =>
            if (fileIndex.isInTestSourceContent(vFile))
              ScopedModule.test(module)
            else
              ScopedModule.compile(module)
        }
      } else None
    }
  }

  override def moduleScopes(m: Module): Set[ScopedModule] = Set(
    ScopedModule.compile(m),
    ScopedModule.test(m)
  )

  override protected def scopeToSearchScope(scope: ScopedModule): GlobalSearchScope =
    scope.module.getModuleTestsWithDependentsScope

  override protected def calculateDependentScopes(scopes: Set[ScopedModule]): Set[ScopedModule] = {
    import scala.collection.mutable
    import scala.jdk.CollectionConverters._

    val visited = mutable.Stack.empty[ScopedModule]
    val stack = scopes.to(mutable.Stack)

    while (stack.nonEmpty) {
      ProgressManager.checkCanceled()
      val scopedModule = stack.pop()
      visited += scopedModule
      ModuleUtilCore
        .getAllDependentModules(scopedModule.module)
        .asScala
        .flatMap(moduleScopes)
        .filter(!visited.contains(_))
        .foreach(stack.push)
    }

    visited.toSet
  }

  private[references] def compilationInfoIndexed(info: CompilationInfo): Unit = {
    val modules = info.affectedModules(project)

    val scopes = info match {
      case sbti: SbtCompilationInfo => modules.map(ScopedModule(_, sbti.configuration))
      case _: JpsCompilationInfo    => modules.flatMap(moduleScopes)
    }

    log.debug(s"Finished indexing compilation info for ${scopes.mkString("[\n\t", "\n\t", "\n]")}.")
    scopes.foreach(markScopeUpToDate)
  }
}

object ScalaDirtyScopeHolder {
  private val log = Logger.getInstance(classOf[ScalaDirtyScopeHolder])

  final case class ScopedModule(module: Module, configuration: Configuration) {
    override def toString: String = s"${module.getName} / $configuration"
  }

  object ScopedModule {
    def compile(m: Module): ScopedModule = ScopedModule(m, Configuration.Compile)
    def test(m:    Module): ScopedModule = ScopedModule(m, Configuration.Test)
  }
}
