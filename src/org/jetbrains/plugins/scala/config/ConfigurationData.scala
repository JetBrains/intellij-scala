package org.jetbrains.plugins.scala.config

import reflect.BeanProperty
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel


/**
 * Pavel.Fatin, 26.07.2010
 */

class ConfigurationData() {
  @BeanProperty
  var compilerLibraryName = ""

  @BeanProperty
  var compilerLibraryLevel: LibraryLevel = _
  
  @BeanProperty
  var compilerOptions = ""
  
  @BeanProperty
  var pluginPaths = Array[String]()
}