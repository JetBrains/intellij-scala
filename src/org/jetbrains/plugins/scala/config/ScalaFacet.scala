package org.jetbrains.plugins.scala.config

import java.lang.String
import com.intellij.facet._
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Pavel.Fatin, 26.07.2010
 */

object ScalaFacet {
  val Id = new FacetTypeId[ScalaFacet]("scala")
  val Type = new ScalaFacetType
  
  def isPresentIn(module: Module) =  findIn(module).isDefined
  
  def findIn(module: Module): Option[ScalaFacet] =
    Option(FacetManager.getInstance(module)).flatMap(manager => Option(manager.getFacetByType(Id)))
  
  def findIn(modules: Array[Module]): Array[ScalaFacet] = modules.flatMap(findIn(_).toList)
  
  def findModulesIn(project: Project) = ModuleManager.getInstance(project).getModules.filter(isPresentIn _)

  def isPresentIn(project: Project): Boolean = !findModulesIn(project).isEmpty

  def findIn(project: Project): Seq[ScalaFacet] = ScalaFacet.findIn(ScalaFacet.findModulesIn(project))

  def findFirstIn(project: Project): Option[ScalaFacet] = findIn(project).headOption
  
  def createIn(module: Module)(action: ScalaFacet => Unit) {
    val facetManager = FacetManager.getInstance(module)
    val model = facetManager.createModifiableModel
    val facet = facetManager.createFacet(ScalaFacet.Type, "Scala", null)
    action(facet)
    model.addFacet(facet)
    model.commit()
  }
} 

class ScalaFacet(module: Module, name: String, 
                 configuration: ScalaFacetConfiguration, underlyingFacet: Facet[_ <: FacetConfiguration]) 
        extends Facet[ScalaFacetConfiguration](ScalaFacet.Type, module, name, configuration, underlyingFacet) {
  
  def compiler = Libraries.findBy(compilerLibraryId, module.getProject)
          .map(new CompilerLibraryData(_))

  def files: Seq[File] = compiler.toList.flatMap(_.files)

  def classpath: String = compiler.map(_.classpath).mkString

  def version: String = compiler.flatMap(_.version).mkString
  
  def javaParameters: Array[String] = getConfiguration.getState.javaParameters
  
  def javaParameters_=(parameters: Array[String]) {
    getConfiguration.getState.updateJavaParameters(parameters)
  }

  def compilerParameters: Array[String] = {
    val plugins = getConfiguration.getState.pluginPaths.map { path =>
      "-Xplugin:" + new CompilerPlugin(path, module).file.getPath
    }
    getConfiguration.getState.compilerParameters ++ plugins
  }
  
  def compilerParameters_=(parameters: Array[String]) {
    getConfiguration.getState.updateCompilerParameters(parameters)
  }
  
  def pluginPaths: Array[String] = getConfiguration.getState.pluginPaths 
  
  def pluginPaths_=(paths: Array[String]) {
    getConfiguration.getState.pluginPaths = paths
  } 

  def fsc: Boolean = getConfiguration.getState.fsc

  def compilerLibraryId_=(id: LibraryId) {
    val data = getConfiguration.getState
    data.compilerLibraryName = id.name
    data.compilerLibraryLevel = id.level
  }

  def compilerLibraryId: LibraryId = {
    val data = getConfiguration.getState
    return new LibraryId(data.compilerLibraryName, data.compilerLibraryLevel)
  }

  def basePackage: Option[String] = {
    val data = getConfiguration.getState
    Option(data.basePackage).filter(!_.isEmpty)
  }
}