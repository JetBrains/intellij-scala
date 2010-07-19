package org.jetbrains.plugins.scala.config

import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.plugins.scala.config.ui.ScalaFacetEditor;
import org.jetbrains.plugins.scala.icons.Icons;

class ScalaFacetSupportProvider extends FrameworkSupportProvider("Scala", "Scala") {
  override def createConfigurable(model: FrameworkSupportModel) = 
    new ScalaSupportConfigurable(new ScalaFacetEditor(model.getProject())); 
  
  override def getIcon() = Icons.SCALA_SMALL_LOGO;
  
  override def isEnabledForModuleType(moduleType: ModuleType[_ <: ModuleBuilder]) = 
    moduleType.isInstanceOf[JavaModuleType]
}