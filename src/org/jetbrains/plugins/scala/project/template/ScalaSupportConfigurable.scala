package org.jetbrains.plugins.scala
package project.template

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ModifiableModelsProvider, ModifiableRootModel}

/**
 * @author Pavel Fatin
 */
class ScalaSupportConfigurable extends FrameworkSupportInModuleConfigurable {
  override def createComponent() = null

  override def createLibraryDescription() = ScalaLibraryDescription

  override def isOnlyLibraryAdded = true

  override def addSupport(module: Module, rootModel: ModifiableRootModel,
                          modifiableModelsProvider: ModifiableModelsProvider) {}
}
