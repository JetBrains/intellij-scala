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
  var maximumHeapSize = 512     
  
  @BeanProperty
  var vmOptions = "-Xss1024k -server"
  
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

  def javaParameters: Array[String] = parse(vmOptions) ++ Array("-Xmx%dm".format(maximumHeapSize))
  
  def compilerParameters: Array[String] = {
    val switches = Array(!warnings -> "nowarn", deprecationWarnings -> "deprecation",
      uncheckedWarnings -> "unchecked", optimiseBytecode -> "optimise",
      explainTypeErrors -> "explaintypes", continuations -> "P:continuations:enable")
    val switchesOptions = switches.collect { 
      case (enabled, name) if enabled => name   
    }                                               
    val debuggingOptions = Array("g:%s".format(debuggingInfoLevel.getOption))
    parse(compilerOptions) ++ (switchesOptions ++ debuggingOptions).map("-" + _) 
  }
  
  private def parse(options: String): Array[String] = {
    val trimmed = options.trim
    if(trimmed.isEmpty) Array.empty else trimmed.split("\\s+") 
  }
  
  private def data = Array(compilerLibraryName, compilerLibraryLevel, maximumHeapSize, vmOptions, warnings, 
    deprecationWarnings, uncheckedWarnings, optimiseBytecode, explainTypeErrors, continuations, 
    debuggingInfoLevel, compilerOptions) ++ pluginPaths
  
  override def equals(obj: Any): Boolean = data.sameElements(obj.asInstanceOf[ConfigurationData].data)
}