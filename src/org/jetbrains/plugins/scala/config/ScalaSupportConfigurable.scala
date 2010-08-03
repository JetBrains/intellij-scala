package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ModifiableRootModel
import java.io.File
import ui.{Choice, ScalaSupportWizard}
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable
import com.intellij.openapi.module.Module

/**
 * Pavel.Fatin, 07.07.2010
 */

class ScalaSupportConfigurable(editor: ScalaSupportWizard) extends FrameworkSupportConfigurable {
  override def getComponent = {
    editor.init()
    editor.getComponent
  }
  
  override def addSupport(module: Module, rootModel: ModifiableRootModel, library: Library) = {
    editor.getChoice match {                                                                       
      case Choice.AddNew => {
        val distribution = new ScalaDistribution(new File(editor.getHome))
        
        distribution.createStandardLibrary(editor.getLibraryName, editor.getLibraryLevel, rootModel)
        
        ScalaFacet.createIn(module) { facet =>
          val level = editor.getCompilerLevel      
          val compilerLibrary = distribution.createCompilerLibrary(editor.getCompilerName, level, rootModel)
          val state = facet.getConfiguration.getState
          state.compilerLibraryName = compilerLibrary.getName
          state.compilerLibraryLevel = level
        }
      }
      case _ =>
    }
  }
}
