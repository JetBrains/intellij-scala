package org.jetbrains.plugins.scala.worksheet.actions

import java.nio.file.Path

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Condition, Key}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.project.settings.CompilerProfileAwareModule
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetSyntheticModule.Log
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.picocontainer.PicoContainer

import scala.annotation.nowarn

/**
 * Lightweight module meant to be attached to a PsiFile via [[org.jetbrains.plugins.scala.project.UserDataKeys.SCALA_ATTACHED_MODULE]].
 *
 * The original purpose of this module is to provide a custom compiler profile
 * (from [[WorksheetFileSettings.getCompilerProfile]]) for a given worksheet during code analysis.
 * Current architecture implies that all the files in a single module (including worksheets) share same compiler options.
 * Thus all methods that check whether some compiler feature is enabled are bound to a module.
 * (see [[org.jetbrains.plugins.scala.project.ScalaModuleSettings]] and [[org.jetbrains.plugins.scala.project.ModuleExt]])
 *
 * There are two option to solve this:
 *  1. carefully rewrite all the methods, to rely on a file, not a module<br>
 *  1. create a synthetic module for a worksheet, which would provide it's compiler profile name
 *
 * I decided to stick to option 2. (though it can seem a little bit hacky) because:
 *  1. looks like this module will be helpful during implementing of
 *     [[https://youtrack.jetbrains.com/issue/SCL-18015 per-worksheet dependencies support]]
 *     (currently all dependencies are bound to a module as well)
 *  1. it requires less code changes
 */
final class WorksheetSyntheticModule(
  virtualFile: VirtualFile,
  val cpModule: Module
) extends Module
  with CompilerProfileAwareModule {

  locally {
    Log.debugSafe(s"new instance $debugId")
  }

  private def debugId =
    s"WorksheetSyntheticModule(${virtualFile.getName}, ${cpModule.getName})"

  override def dispose(): Unit =
    Log.debug(s"disposed $debugId")

  override def getDisposed: Condition[_] = cpModule.getDisposed

  override def isLoaded: Boolean = cpModule.isLoaded

  // doesn't have any file, representing the module
  override def getModuleFile: VirtualFile = null
  override def getModuleNioFile: Path = null

  override def getProject: Project = cpModule.getProject
  // not quite clear, should the name be unique or not...
  override def getName: String = s"worksheet synthetic module for: ${virtualFile.getName}"
  override def getModuleTypeName: String = super.getModuleTypeName
  override def isDisposed: Boolean = cpModule.isDisposed

  override def getComponent[T](interfaceClass: Class[T]): T = cpModule.getComponent(interfaceClass)
  override def getPicoContainer: PicoContainer = cpModule.getPicoContainer
  override def getMessageBus: MessageBus = cpModule.getMessageBus

  override def getUserData[T](key: Key[T]): T = cpModule.getUserData(key)
  override def putUserData[T](key: Key[T], value: T): Unit = cpModule.putUserData(key, value)

  //noinspection ScalaDeprecation
  @nowarn("cat=deprecation")
  override def setOption(key: String, value: String): Unit = cpModule.setOption(key, value)
  //noinspection ScalaDeprecation
  @nowarn("cat=deprecation")
  override def getOptionValue(key: String): String = cpModule.getOptionValue(key)

  private def worksheetFileScope: GlobalSearchScope =
    GlobalSearchScope.fileScope(getProject, virtualFile)

  override def getModuleRuntimeScope(includeTests: Boolean): GlobalSearchScope =
    cpModule.getModuleRuntimeScope(includeTests)

  override def getModuleScope: GlobalSearchScope =
    worksheetFileScope
  override def getModuleScope(includeTests: Boolean): GlobalSearchScope =
    worksheetFileScope
  override def getModuleWithLibrariesScope: GlobalSearchScope =
    worksheetFileScope.union(cpModule.getModuleWithLibrariesScope)
  override def getModuleWithDependenciesScope: GlobalSearchScope =
    worksheetFileScope.union(cpModule.getModuleWithDependenciesScope)
  override def getModuleWithDependenciesAndLibrariesScope(includeTests: Boolean): GlobalSearchScope =
    worksheetFileScope.union(cpModule.getModuleWithDependenciesAndLibrariesScope(includeTests))
  override def getModuleWithDependentsScope: GlobalSearchScope =
    worksheetFileScope // no module can depend on worksheet for now
  override def getModuleTestsWithDependentsScope: GlobalSearchScope =
    worksheetFileScope

  // not 100% clear what should go here, and what it is needed for in practice
  override def getModuleContentScope: GlobalSearchScope =
    worksheetFileScope
  override def getModuleContentWithDependenciesScope: GlobalSearchScope =
    worksheetFileScope

  override def compilerProfileName: String =
    WorksheetFileSettings(getProject, virtualFile).getCompilerProfileName
}

object WorksheetSyntheticModule {

  private val Log = Logger.getInstance(this.getClass)
}
