package org.jetbrains.plugins.scala
package project.converter

import org.jdom.Element
import org.jetbrains.plugins.scala.project.converter.ScalaCompilerSettings._

import scala.xml.Node

/**
 * @author Pavel Fatin
 */
case class ScalaCompilerSettings(compileOrder: String,
                                 warnings: Boolean,
                                 deprecationWarnings: Boolean,
                                 uncheckedWarnings: Boolean,
                                 optimiseBytecode: Boolean,
                                 explainTypeErrors: Boolean,
                                 continuations: Boolean,
                                 debuggingInfoLevel: String,
                                 additionalCompilerOptions: collection.Seq[String],
                                 compilerPlugins: collection.Seq[String]) {

  def isDefault: Boolean = this == Default

  def toXml: Seq[Node] = {
    when(compileOrder != DefaultComipileOrder)(<option name="compilerOrder" value={compileOrder}/>) ++
    when(!warnings)(<option name="warnings" value={warnings.toString}/>) ++
    when(deprecationWarnings)(<option name="deprecationWarnings" value={deprecationWarnings.toString}/>) ++
    when(uncheckedWarnings)(<option name="uncheckedWarnings" value={uncheckedWarnings.toString}/>) ++
    when(optimiseBytecode)(<option name="optimiseBytecode" value={optimiseBytecode.toString}/>) ++
    when(explainTypeErrors)(<option name="explainTypeErrors" value={explainTypeErrors.toString}/>) ++
    when(continuations)(<option name="continuations" value={continuations.toString}/>) ++
    when(debuggingInfoLevel != DefaultDebuggingLevel)(<option name="debuggingInfoLevel" value={debuggingInfoLevel}/>) ++
    when(additionalCompilerOptions.nonEmpty)(
      <parameters>{additionalCompilerOptions.map(option => <parameter value={option}/>)}</parameters>) ++
    when(compilerPlugins.nonEmpty)(
      <plugins>{compilerPlugins.map(option => <plugin path={option}/>)}</plugins>)
  }

  private def when[T](b: Boolean)(value: => T): Seq[T] = if (b) Seq(value) else Seq.empty
}

object ScalaCompilerSettings {
  private val DebugginInfoLevels = Map(
    "None" -> "None",
    "Source file attribute" -> "Source",
    "Source and line number information" -> "Line",
    "Source, line number and local variable information" -> "Vars",
    "Complete, no tail call optimization" -> "Notailcalls")

  private val DefaultComipileOrder = "Mixed"

  private val DefaultDebuggingLevel = "Vars"

  val Default: ScalaCompilerSettings = from(new FacetProperties(new Element("empty")))

  def from(properties: FacetProperties): ScalaCompilerSettings = {
    val debuggingLevel = properties.option("debuggingInfoLevel")
            .fold(DefaultDebuggingLevel)(DebugginInfoLevels)

    new ScalaCompilerSettings(
      compileOrder = properties.string("compileOrder", DefaultComipileOrder),
      warnings = properties.boolean("warnings", default = true),
      deprecationWarnings = properties.boolean("deprecationWarnings"),
      uncheckedWarnings = properties.boolean("uncheckedWarnings"),
      optimiseBytecode = properties.boolean("optimiseBytecode"),
      explainTypeErrors = properties.boolean("optimiseBytecode"),
      continuations = properties.boolean("continuations"),
      debuggingInfoLevel = debuggingLevel,
      additionalCompilerOptions = properties.seq("compilerOptions"),
      compilerPlugins = properties.array("pluginPaths")
    )
  }
}