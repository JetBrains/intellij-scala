package org.jetbrains.plugins.scala.config

import com.intellij.facet.{FacetConfiguration, Facet, FacetType}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.openapi.module.{JavaModuleType, ModuleType, Module}

/**
 * Pavel.Fatin, 26.07.2010
 */

class ScalaFacetType extends FacetType[ScalaFacet, ScalaFacetConfiguration](ScalaFacet.Id, "scala", "Scala") {
  override def getIcon = Icons.SCALA_SMALL_LOGO
  
  def isSuitableModuleType(moduleType: ModuleType[_ <: ModuleBuilder]) = 
    moduleType.isInstanceOf[JavaModuleType] || moduleType.getId == "PLUGIN_MODULE" || moduleType.isInstanceOf[ScalaFacetAvailabilityMarker]
  
  def createDefaultConfiguration = {
    new ScalaFacetConfiguration
  }

  def createFacet(module: Module, name: String, configuration: ScalaFacetConfiguration,
                  underlyingFacet: Facet[_ <: FacetConfiguration]) = {
    new ScalaFacet(module, name, configuration, underlyingFacet)
  }

  // workaround for FacetEditorFacadeImpl.addFacetNode(Facet) that uses object equality to compare facet types
  override def equals(obj: Any) = obj match {
    case facetType: ScalaFacetType => getStringId == facetType.getStringId
    case _ => false
  }
}