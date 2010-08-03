package org.jetbrains.plugins.scala.config

import ui.ScalaSupportWizard
import com.intellij.ide.util.frameworkSupport.{FrameworkSupportProvider, FrameworkSupportModel}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.openapi.module.{Module, JavaModuleType, ModuleType}

class ScalaFacetSupportProvider extends FrameworkSupportProvider("Scala", "Scala") {
  override def createConfigurable(model: FrameworkSupportModel) = 
    new ScalaSupportConfigurable(new ScalaSupportWizard(model.getProject())); 
  
  override def getIcon() = Icons.SCALA_SMALL_LOGO;
  
  override def isEnabledForModuleType(moduleType: ModuleType[_ <: ModuleBuilder]) = 
    moduleType.isInstanceOf[JavaModuleType] || moduleType.getId == "PLUGIN_MODULE"

  override def isSupportAlreadyAdded(module: Module): Boolean = ScalaFacet.isPresentIn(module)
}

