package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.module.Module
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable
import java.io.File
import ui.{Choice, ScalaFacetEditor}
/**
 * Pavel.Fatin, 07.07.2010
 */

class ScalaSupportConfigurable(editor: ScalaFacetEditor) extends FrameworkSupportConfigurable {
  def getComponent = {
    editor.init()
    editor.getComponent
  }
  
  def addSupport(module: Module, rootModel: ModifiableRootModel, library: Library) = {
    editor.getChoice match {
      case Choice.UseExisting => editor.getExistingLibrary.attachTo(rootModel)
      case Choice.AddNew => {
        val distribution = new ScalaDistribution(new File(editor.getHome))
        distribution.createLibrary(editor.getName, editor.getLevel, module.getProject, rootModel)
      }
      case Choice.DoNothing => // do nothing 
    } 
  }
}