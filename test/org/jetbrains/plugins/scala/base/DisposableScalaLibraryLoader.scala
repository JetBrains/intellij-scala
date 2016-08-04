package org.jetbrains.plugins.scala.base

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer

/** Special version of library loader whose lifecycle is managed automatically by IDEA disposer mechanism.
  *
  * Loader will be disposed together with the module it's attached to.
  */
class DisposableScalaLibraryLoader(project: Project,
                                   module: Module,
                                   rootPath: String,
                                   isIncludeReflectLibrary: Boolean = false,
                                   javaSdk: Option[Sdk] = None,
                                   additionalLibraries: Array[String] = Array.empty)
  extends ScalaLibraryLoader(project, module, rootPath, isIncludeReflectLibrary, javaSdk, additionalLibraries) with Disposable {

  Disposer.register(module, this)

  override def disposeLibraries(): Unit = ()  // libraries are automatically disposed by module

  override def dispose(): Unit = clean()
}
