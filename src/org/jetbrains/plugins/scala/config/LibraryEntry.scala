package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import com.intellij.openapi.roots.libraries.Library
import java.io.File
import FileAPI._
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager, OrderRootType}

/**
 * Pavel.Fatin, 26.07.2010
 */

object LibraryEntry {
  def compilerByName(project: Project, name: String) = compilers(project).find(_.name == name)
  
  def compilers(project: Project): Array[ScalaCompilerEntry] = 
    entries(project, new ScalaCompilerEntry(_, _)).toArray
  
  def libraries(project: Project): Array[ScalaLibraryEntry] = 
    entries(project, new ScalaLibraryEntry(_, _)).toArray
  
  private def entries[T <: LibraryEntry](project: Project, constructor: (LibraryLevel, Library) => T) = {
    val globalEntries = globalLibraries.map(constructor(LibraryLevel.GLOBAL, _)) 
    val projectEntries = projectLibraries(project).map(constructor(LibraryLevel.PROJECT, _))
    (globalEntries ++ projectEntries).filter(_.valid).sortBy(_.name)
  }
  
  private def globalLibraries = ApplicationLibraryTable.getApplicationTable.getLibraries.view

  private def projectLibraries(project: Project) = 
    if(project == null) Seq.empty else ProjectLibraryTable.getInstance(project).getLibraries.view
  
  private def moduleLibraries(module: Module) = inReadAction {
    ModuleRootManager.getInstance(module).getOrderEntries.view
            .filter(_.isInstanceOf[LibraryOrderEntry])
            .map(_.asInstanceOf[LibraryOrderEntry].getLibrary)
            .filter(_ != null)
  }
  
  def nameClashes(name: String, level: LibraryLevel, project: Project): Boolean = {
    val libraries: Seq[Library] = level match { 
      case LibraryLevel.GLOBAL => globalLibraries
      case LibraryLevel.PROJECT => projectLibraries(project)
      case _ => Seq.empty
    }                         
    libraries.exists(_.getName == name)
  }
  
  def uniqueName(name: String, level: LibraryLevel, project: Project): String = {
    def postfix(name: String, n: Int = 1): String = {
      val postfixed = "%s (%d)".format(name, n)  
      if(nameClashes(postfixed, level, project)) postfix(name, n + 1) else postfixed
    }
    val pure  = name.replaceFirst("""\(\d+\)$""", "")
    if(nameClashes(pure, level, project)) postfix(pure) else pure 
  }
}

abstract class LibraryEntry(val level: LibraryLevel, delegate: Library, prefix: String, bundle: String) {
  def name: String = delegate.getName
  
  def valid: Boolean = jar.isDefined

  def version: String = jar.flatMap(readProperty(_, bundle, "version.number")).mkString 

  def classpath: String = files.map(_.getPath).mkString(File.pathSeparator)
  
  def files: Seq[File] = delegate.getFiles(OrderRootType.CLASSES).map(_.toFile)
  
  def jar: Option[File] = files.find(_.getName.startsWith(prefix)) 
}

class ScalaCompilerEntry(level: LibraryLevel, delegate: Library) 
        extends LibraryEntry(level, delegate, "scala-compiler", "compiler.properties")

class ScalaLibraryEntry(level: LibraryLevel, delegate: Library) 
        extends LibraryEntry(level, delegate, "scala-library", "library.properties")