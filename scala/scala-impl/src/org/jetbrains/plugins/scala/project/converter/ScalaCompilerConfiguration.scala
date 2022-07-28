package org.jetbrains.plugins.scala.project.converter

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext}
import com.intellij.openapi.components.StorageScheme

import java.nio.file.Path
import scala.xml.Elem

class ScalaCompilerConfiguration(defaultSettings: ScalaCompilerSettings, profiles: Seq[ScalaCompilerSettingsProfile]) extends XmlConversion {
  def createIn(context: ConversionContext): Option[Path] = {
    val optionsElement = createOptionsElement()

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        Some(addDirectoryBasedOptions(optionsElement, context))
      case StorageScheme.DEFAULT =>
        addProjectBasedOptions(optionsElement, context)
        None
    }
  }

  private def addDirectoryBasedOptions(options: Elem, context: ConversionContext): Path = {
    val base = Option(context.getSettingsBaseDir)
            .getOrElse(throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    val file = base.resolve("scala_compiler.xml").toFile
    val componentElement = <project version="4"> {options} </project>
    Files.write(formatXml(componentElement).getBytes, file)
    file.toPath
  }

  private def addProjectBasedOptions(options: Elem, context: ConversionContext): Unit = {
    val rootElement = context.getProjectSettings.getRootElement
    rootElement.addContent(asJava(options))
  }

  private def createOptionsElement(): Elem = {
    <component name="ScalaCompilerConfiguration">
      {defaultSettings.toXml}
      {profiles.map(_.toXml)}
    </component>
  }
}
