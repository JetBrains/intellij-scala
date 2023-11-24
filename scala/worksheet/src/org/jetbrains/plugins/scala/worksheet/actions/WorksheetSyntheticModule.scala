package org.jetbrains.plugins.scala.worksheet.actions


import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.{ExtensionsArea, PluginDescriptor, PluginId}
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ReflectionUtil
import org.jetbrains.plugins.scala.compiler.highlighting.SyntheticModule
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.project.settings.CompilerProfileAwareModule
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetSyntheticModule.Log
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import java.nio.file.Path

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
) extends ModuleDelegate(cpModule)
  with CompilerProfileAwareModule
  with SyntheticModule {

  locally {
    Log.debugSafe(s"new instance $debugId")
  }

  private def debugId =
    s"WorksheetSyntheticModule(${virtualFile.getName}, ${cpModule.getName})"

  override def dispose(): Unit =
    Log.debug(s"disposed $debugId")

  // doesn't have any file, representing the module
  override def getModuleFile: VirtualFile = null
  override def getModuleNioFile: Path = null

  // not quite clear, should the name be unique or not...
  override def getName: String = s"worksheet synthetic module for: ${virtualFile.getName}"

  private def worksheetFileScope: GlobalSearchScope =
    GlobalSearchScope.fileScope(getProject, virtualFile)

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

  override def hasComponent(interfaceClass: Class[_]): Boolean = cpModule.hasComponent(interfaceClass)

  override def instantiateClass[T](className: String, pluginDescriptor: PluginDescriptor): T =
    ReflectionUtil.newInstance(loadClass(className, pluginDescriptor))

  override def getExtensionArea: ExtensionsArea = throw new IllegalStateException("not implemented")

  //noinspection ApiStatus,UnstableApiUsage
  override def instantiateClass[T](aClass: Class[T], pluginId: PluginId): T =
    cpModule.instantiateClass(aClass, pluginId)

  override def underlying: Module = cpModule
}

object WorksheetSyntheticModule {

  private val Log = Logger.getInstance(this.getClass)
}
