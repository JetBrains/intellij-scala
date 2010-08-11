package org.jetbrains.plugins.scala.config

import reflect.BeanProperty
import org.jetbrains.annotations.Nullable

/**
 * Pavel.Fatin, 26.07.2010
 */

class ConfigurationData() {
  @BeanProperty
  var compilerLibraryName = ""

  @BeanProperty
  @Nullable
  var compilerLibraryLevel: LibraryLevel = _
  
  @BeanProperty
  var compilerOptions = ""
  
  @BeanProperty
  var pluginPaths = Array[String]()
}