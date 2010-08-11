package org.jetbrains.plugins.scala.config

import reflect.BeanProperty
import org.jetbrains.annotations.Nullable
import java.util.Arrays

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
  var maximumHeapSize = 256
  
  @BeanProperty
  var warnings = true

  @BeanProperty
  var deprecationWarnings = false
  
  @BeanProperty
  var uncheckedWarnings = false
  
  @BeanProperty
  var optimiseBytecode = false
  
  @BeanProperty
  var explainTypeErrors = false
  
  @BeanProperty
  var continuations = false
  
  @BeanProperty
  var debuggingInfoLevel = DebuggingInfoLevel.Vars
  
  @BeanProperty
  var compilerOptions = ""
  
  @BeanProperty
  var pluginPaths = Array[String]()

  def javaParameters: Array[String] = Array("-Xmx%dm".format(maximumHeapSize)) 
  
  def compilerParameters: Array[String] = {
    val switches = List(!warnings -> "nowarn", deprecationWarnings -> "deprecation",
      uncheckedWarnings -> "unchecked", optimiseBytecode -> "optimise",
      explainTypeErrors -> "explaintypes", continuations -> "P:continuations:enable")
    val options = switches.collect { 
      case (enabled, name) if enabled => name
    }
    val seq = ("g:%s".format(debuggingInfoLevel.getOption) :: options).map("-" + _)
    if(compilerOptions.isEmpty) seq.toArray else (compilerOptions :: seq).toArray
  }
  
  private def data = Array(compilerLibraryName, compilerLibraryLevel, maximumHeapSize, warnings, 
    deprecationWarnings, uncheckedWarnings, optimiseBytecode, explainTypeErrors, continuations, 
    debuggingInfoLevel, compilerOptions) ++ pluginPaths
  
  override def equals(obj: Any): Boolean = data.sameElements(obj.asInstanceOf[ConfigurationData].data)
}