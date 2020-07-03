package org.jetbrains.plugins.scala
package lang.formatting.settings

import java.io.File
import java.util
import java.util.Collections

import com.intellij.conversion._
import com.intellij.conversion.impl.{ComponentManagerSettingsImpl, ConversionContextImpl}
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.SystemProperties
import org.jdom.Document

import scala.collection.JavaConverters._

/**
 * @author Alefas
 * @since 21/05/14.
 */
class ImportPanelConverterProvider extends ConverterProvider("ImportPanelConverterProvider") {
  override def getConversionDescription: String = {
    ScalaBundle.message("scala.imports.settings.will.be.moved.to.code.style.settings")
  }

  override def createConverter(context: ConversionContext): ProjectConverter = {
    import org.jdom.Element
    val actualSettingsSet =
      Set(
        "addFullQualifiedImports",
        "addImportMostCloseToReference",
        "classCountToUseImportOnDemand",
        "importMembersUsingUnderScore",
        "importShortestPathForAmbiguousReferences",
        "importsWithPrefix",
        "sortImports"
      )

    def getElements: Seq[Element] = {
      context.getSettingsBaseDir.listFiles().find(_.getName == "scala_settings.xml") match {
        case Some(file) =>
          import com.intellij.conversion.impl.ConversionContextImpl
          context match {
            case context: ConversionContextImpl =>
              val settings = new ComponentManagerSettingsImpl(file.toPath, context) {}
              val children = settings.getRootElement.getChildren.asScala
              children.find(_.getName == "component") match {
                case Some(componentChild) =>
                  componentChild.getChildren.asScala.filter { elem =>
                    elem.getName == "option" && elem.getAttribute("name") != null &&
                      actualSettingsSet.contains(elem.getAttribute("name").getValue)
                  }
                case None => Seq.empty
              }
            case _ => Seq.empty
          }
        case _ => Seq.empty
      }
    }

    new ProjectConverter {
      override def isConversionNeeded: Boolean = {
        import com.intellij.openapi.components.StorageScheme
        if (context.getStorageScheme == StorageScheme.DEFAULT) return false
        getElements.nonEmpty
      }

      override def getAdditionalAffectedFiles: util.Collection[File] = {
        context.getSettingsBaseDir.listFiles().find(_.getName == "codeStyleSettings.xml") match {
          case Some(file) => Collections.singleton(file)
          case None => Collections.emptyList()
        }
      }

      override def processingFinished(): Unit = {
        context.getSettingsBaseDir.listFiles().find(_.getName == "codeStyleSettings.xml") match {
          case Some(file) =>
            context match {
              case context: ConversionContextImpl =>
                val settings = new ComponentManagerSettingsImpl(file.toPath, context) {}
                val root = settings.getRootElement
                for {
                  component <- Option(root.getChild("component"))
                  option <- Option(component.getChild("option"))
                  value <- Option(option.getChild("value"))
                } {
                  var settingsValue = value.getChild("ScalaCodeStyleSettings")
                  if (settingsValue == null) {
                    settingsValue = new Element("ScalaCodeStyleSettings")
                    value.addContent(settingsValue)
                  }
                  getElements.foreach(elem => settingsValue.addContent(elem.clone()))
                }
                JDOMUtil.writeDocument(new Document(root.clone()), file, SystemProperties.getLineSeparator)
            }
          case _ =>
        }
      }
    }
  }
}
