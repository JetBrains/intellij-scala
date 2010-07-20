package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.project.Project
import java.io.File
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots._
import collection.Seq
import com.intellij.execution.ExecutionException

/**
 * Pavel.Fatin, 05.07.2010
 */

object ScalaLibrary {
  def findAll(project: Project): Array[ScalaLibrary] = {
    val all = wrap(globalLibraries, LibraryLevel.GLOBAL) ++ 
            wrap(projectLibraries(project), LibraryLevel.PROJECT)
    all.filter(_.valid).sortBy(_.name).toArray
  }
  
  def isPresentIn(module: Module): Boolean = !findIn(module).isEmpty
  
  def isPresentIn(modules: Array[Module]): Boolean = modules.exists(isPresentIn _)
  
  @throws(classOf[ExecutionException])
  def tryToFindIn(module: Module): ScalaLibrary = ScalaLibrary.findIn(module).toList match {
    case library :: Nil => {
      library.problems.foreach(error => throw new ExecutionException(error.message))
      library
    }
    case Nil => throw new ExecutionException("No Scala SDK configured for module " + module.getName)
    case _ => throw new ExecutionException("Multiple Scala SDKs configured for module " + module.getName)
  }
    
  private def findIn(module: Module): Array[ScalaLibrary] = 
    wrap(moduleLibraries(module), LibraryLevel.MODULE).filter(_.valid).toArray
  
  def findIn(modules: Array[Module]): Array[ScalaLibrary] = modules.flatMap(findIn(_).toSeq).toArray

  def hasConsistentVersions(modules: Array[Module]): Boolean = 
    modules.flatMap(findIn(_).map(_.version).toSeq).distinct.size < 2 
  
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
      if(nameClashes(postfixed, level, project)) postfix(postfixed, n + 1) else postfixed
    }
    if(nameClashes(name, level, project)) postfix(name) else name 
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

  private def wrap(libraries: Seq[Library], level: LibraryLevel) = {
    libraries.map(new ScalaLibrary(_, level))
  }
}

class ScalaLibrary(delegate: Library, val level: LibraryLevel) extends ScalaSDK {
  def name = delegate.getName
  
  override protected def compilerFile = libraryFile.flatMap { library => 
    findCompilerBeside(library).orElse(findCompilerBesideMaven(library))
  }
  
  private def findCompilerBeside(library: File) = library.parent.flatMap(_.findByName(Compiler.classes))
  
  private def findCompilerBesideMaven(library: File) = library.parent.flatMap { version =>
    version.parent.filter(_.getName == "scala-library").flatMap { libraries =>
      libraries.parent.flatMap { root =>
        root.findByName("scala-compiler").flatMap { compilers =>
          compilers.findByName(version.getName).flatMap(_.listFiles.find(_.getName.endsWith(".jar")))
        }
      }
    }
  }

  override protected def libraryFile = delegate.getFiles(OrderRootType.CLASSES)
          .find(_.namedLike(Library.classes)).map(_.toFile)
  
  def hasDocs = delegate.getRootProvider.getFiles(JavadocOrderRootType.getInstance).exists(_.exists)
  
  def attachTo(model: ModifiableRootModel) {
      model.addLibraryEntry(delegate)
  }
  
  def classpath: String = delegate.getFiles(OrderRootType.CLASSES).view
          .map(_.toFile.getPath).mkString(File.pathSeparator)
}