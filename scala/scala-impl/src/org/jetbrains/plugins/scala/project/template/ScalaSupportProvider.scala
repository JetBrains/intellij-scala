package org.jetbrains.plugins.scala
package project.template

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{Module, ModuleType, ModuleTypeId}
import com.intellij.openapi.roots.ui.configuration.FacetsProvider
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaSupportProvider extends FrameworkSupportInModuleProvider {
  override def getIcon = Icons.SCALA_SMALL_LOGO

  override def getFrameworkType = ScalaFrameworkType.Instance

  override def isEnabledForModuleType(moduleType: ModuleType[_ <: ModuleBuilder]): Boolean = {
    val id = moduleType.getId
    id == ModuleTypeId.JAVA_MODULE || id == "PLUGIN_MODULE" // PluginModuleType.getInstance.getId
  }

  override def isSupportAlreadyAdded(module: Module, facetsProvider: FacetsProvider): Boolean = module.hasScala

  override def createConfigurable(model: FrameworkSupportModel) = new ScalaSupportConfigurable()
}

