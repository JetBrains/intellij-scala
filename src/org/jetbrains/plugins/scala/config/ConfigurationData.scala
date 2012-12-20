package org.jetbrains.plugins.scala.config

import scala.beans.BeanProperty
import org.jetbrains.annotations.Nullable
import scala.util.matching.Regex
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel._

/**
 * Pavel.Fatin, 26.07.2010
 */

class ConfigurationData() {
  @BeanProperty
  var fsc = false

  @BeanProperty
  var compilerLibraryName = ""

  @BeanProperty
  @Nullable
  var compilerLibraryLevel: LibraryLevel = _

  @BeanProperty
  var maximumHeapSize = 512     
  
  @BeanProperty
  var vmOptions = "-Xss1m -server"
  
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

  @BeanProperty
  var basePackage: String = ""

  @BeanProperty
  var languageLevel: String = DEFAULT_LANGUAGE_LEVEL.toString

  @BeanProperty
  var compileOrder: CompileOrder = CompileOrder.Mixed

  def javaParameters: Array[String] = parse(vmOptions) ++ Array("-Xmx%dm".format(maximumHeapSize))
  
  def compilerParameters: Array[String] = {
    val propertiesNames = Properties.filter(_.value).map(_.name)
    val debuggingOption = "-g:%s".format(debuggingInfoLevel.getOption)
    parse(compilerOptions) ++ (propertiesNames ++ Array(debuggingOption)) 
  }
  
  private def parse(options: String): Array[String] = {
    val trimmed = options.trim
    if(trimmed.isEmpty) Array.empty else trimmed.split("\\s+") 
  }

  def updateJavaParameters(parameters: Seq[String]) {
    val (size, remainder) = extract("(?i)-Xmx(\\d+)m".r, parameters)
    size.foreach { digits =>
      maximumHeapSize = digits.toInt
    }
    vmOptions = remainder.mkString(" ")
  }
  
  def updateCompilerParameters(parameters: Seq[String]) {
    val names = Properties.map(_.name)
    val (knowns, unknowns) = parameters.partition(names.contains(_))
    Properties.foreach { property =>
      property.value = knowns.contains(property.name)
    }
    val (level, remainder) = extract("-g:(\\w+)".r, unknowns)
    level.flatMap(name => DebuggingInfoLevel.values.find(_.getOption == name)).foreach { 
      debuggingInfoLevel = _
    }
    compilerOptions = remainder.mkString(" ")
  }
  
  def extract(pattern: Regex, parameters: Seq[String]) = {
    val levels = parameters.collect {
      case parameter@pattern(value) => (parameter, value)
    }
    val (objects, values) = levels.unzip
    (values.headOption, parameters diff objects)
  }
  
  private class Property(val name: String, getter: => Boolean, setter: Boolean => Unit) {
    def value = getter
    def value_=(b: Boolean) {setter(b)}
  }
  
  private object Warnings extends Property("-nowarn", !warnings, b => warnings = !b)
  private object DeprecationWarnings extends Property("-deprecation", deprecationWarnings, deprecationWarnings = _)
  private object UncheckedWarnings extends Property("-unchecked", uncheckedWarnings, uncheckedWarnings = _)
  private object OptimiseBytecode extends Property("-optimise", optimiseBytecode, optimiseBytecode = _)
  private object ExplainTypeErrors extends Property("-explaintypes", explainTypeErrors, explainTypeErrors = _)
  private object Continuations extends Property("-P:continuations:enable", continuations, continuations = _)
  
  private val Properties = Array(
    Warnings, DeprecationWarnings, UncheckedWarnings, OptimiseBytecode, ExplainTypeErrors, Continuations) 
  
  private def data = Array(fsc, compilerLibraryName, compilerLibraryLevel, compileOrder, maximumHeapSize, vmOptions,
    debuggingInfoLevel, compilerOptions, basePackage, languageLevel) ++ Properties.map(_.value) ++ pluginPaths
  
  override def equals(obj: Any): Boolean = data.sameElements(obj.asInstanceOf[ConfigurationData].data)
}