package org.jetbrains.plugins.scala
package project.converter

import java.io.{File, StringReader}

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext}
import com.intellij.openapi.components.StorageScheme
import org.jdom.Element
import org.jdom.input.SAXBuilder

import scala.xml.{Elem, PrettyPrinter}

/**
 * @author Pavel Fatin
 */
private case class ScalaCompilerOptions(warnings: Boolean,
                                        deprecationWarnings: Boolean,
                                        uncheckedWarnings: Boolean,
                                        optimiseBytecode: Boolean,
                                        explainTypeErrors: Boolean,
                                        continuations: Boolean,
                                        debuggingInfoLevel: String,
                                        additionalCompilerOptions: Seq[String]) {

  def createIn(context: ConversionContext): Option[File] = {
    val optionsElement = createOptionsElement()

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        Some(addDirectoryBasedOptions(optionsElement, context))
      case StorageScheme.DEFAULT =>
        addProjectBasedOptions(optionsElement, context)
        None
    }
  }

  private def addDirectoryBasedOptions(options: Elem, context: ConversionContext): File = {
    val base = Option(context.getSettingsBaseDir)
            .getOrElse(throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    val file = new File(base, "scala_compiler.xml")
    val componentElement = <project version="4"> {options} </project>
    Files.write(formatXml(componentElement).getBytes, file)
    file
  }

  private def addProjectBasedOptions(options: Elem, context: ConversionContext) {
    val rootElement = context.getProjectSettings.getRootElement
    val optionsElement = parseXml(formatXml(options))
    rootElement.addContent(optionsElement)
  }

  private def createOptionsElement(): Elem = {
    <component name="ScalaCompilerConfiguration">
      <option name="warnings" value={warnings.toString} />
      <option name="deprecationWarnings" value={deprecationWarnings.toString} />
      <option name="uncheckedWarnings" value={uncheckedWarnings.toString} />
      <option name="optimiseBytecode" value={optimiseBytecode.toString} />
      <option name="explainTypeErrors" value={explainTypeErrors.toString} />
      <option name="continuations" value={continuations.toString} />
      <option name="debuggingInfoLevel" value={debuggingInfoLevel} />
      <parameters>
        {additionalCompilerOptions.map(option => <parameter value={option} />)}
      </parameters>
      <plugins>
      </plugins>
    </component>
  }

  private def formatXml(element: Elem): String = {
    val printer = new PrettyPrinter(180, 2)
    printer.format(element)
  }

  private def parseXml(xml: String): Element = {
    val builder = new SAXBuilder()
    val document = builder.build(new StringReader(xml))
    document.detachRootElement()
  }
}

private object ScalaCompilerOptions {
  private val DebugginInfoLevels = Seq("None", "Source", "Line", "Vars", "Notc")

  def generalize(others: Seq[ScalaCompilerOptions]): ScalaCompilerOptions = {
    def exists(predicate: ScalaCompilerOptions => Boolean) = others.exists(predicate)

    val debuggingLevel = others.map(_.debuggingInfoLevel).maxBy(DebugginInfoLevels.indexOf(_))

    ScalaCompilerOptions(
      warnings = exists(_.warnings),
      deprecationWarnings = exists(_.deprecationWarnings),
      uncheckedWarnings = exists(_.uncheckedWarnings),
      optimiseBytecode = exists(_.optimiseBytecode),
      explainTypeErrors = exists(_.explainTypeErrors),
      continuations = exists(_.continuations),
      debuggingInfoLevel = if (DebugginInfoLevels.contains(debuggingLevel)) debuggingLevel else "Vars",
      additionalCompilerOptions = others.flatMap(_.additionalCompilerOptions).distinct)
  }
}