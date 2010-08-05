package org.jetbrains.plugins.scala.config

import java.lang.String
import com.intellij.facet._
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Pavel.Fatin, 26.07.2010
 */

object ScalaFacet{
  val Id = new FacetTypeId[ScalaFacet]("scala")
  val Type = new ScalaFacetType
  
  def isPresentIn(module: Module) =  findIn(module).isDefined
  
  def isCompilerConfiguredFor(module: Module) = findIn(module).flatMap(_.compiler).isDefined
  
  def findIn(module: Module) = Option(FacetManager.getInstance(module).getFacetByType(Id)) 
  
  def findIn(modules: Array[Module]): Array[ScalaFacet] = modules.flatMap(findIn(_).toList)
  
  def findModulesIn(project: Project) = ModuleManager.getInstance(project).getModules.filter(isPresentIn _)
  
  def createIn(module: Module)(action: ScalaFacet => Unit) = {
    var facetManager = FacetManager.getInstance(module)
    var model = facetManager.createModifiableModel
    var facet = facetManager.createFacet(ScalaFacet.Type, "Scala", null)
    action(facet)
    model.addFacet(facet)
    model.commit
  }
} 

class ScalaFacet(module: Module, name: String, 
                 configuration: ScalaFacetConfiguration, underlyingFacet: Facet[_ <: FacetConfiguration]) 
        extends Facet[ScalaFacetConfiguration](ScalaFacet.Type, module, name, configuration, underlyingFacet) {
  
  private def compiler = Libraries.findBy(getCompilerLibraryId, module.getProject)
          .map(new CompilerLibraryData(_))

  def configured: Boolean = compiler.isDefined

  def files: Seq[File] = compiler.toList.flatMap(_.files)

  def classpath: String = compiler.map(_.classpath).mkString

  def version: String = compiler.map(_.version).mkString
  
  def options: String = getConfiguration.getState.compilerOptions
  
  def plugins: Array[String] = getConfiguration.getState.pluginPaths.map { path =>
      new CompilerPlugin(path, module).file.getPath
  }
  
  def setCompilerLibraryId(id: LibraryId): Unit = {
    val data = getConfiguration.getState
    data.compilerLibraryName = id.name
    data.compilerLibraryLevel = id.level
  }

  def getCompilerLibraryId: LibraryId = {
    val data = getConfiguration.getState
    return new LibraryId(data.compilerLibraryName, data.compilerLibraryLevel)
  }
} 