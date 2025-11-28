package org.jetbrains.plugins.scala.base

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.{Language => InputLanguage}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}

import scala.reflect.ClassTag

trait ScalaCodeParsing {

  protected def scalaCodeParsingFeatures: ScalaFeatures =
    ScalaFeatures.onlyByVersion(
      this match {
        case sdkOwner: ScalaSdkOwner => sdkOwner.version
        case _ => ScalaVersion.default
      }
    )

  private def checkedCodeParsingFeatures: ScalaFeatures = this match {
    case sdkOwner: ScalaSdkOwner =>
      val features = scalaCodeParsingFeatures
      assert(
        sdkOwner.version.languageLevel == features.languageLevel,
        s"""
           |Test class ${getClass.getName} inherits both from ScalaSdkOwner and ScalaCodeParsing.
           |In this case sdkOwner.version and scalaCodeParsingFeatures need to have the same language level
           |to prevent unexpected behaviour.
           |
           |If you really need to parse a file in a different version, explicitly pass the appropriate ScalaFeatures
           |to the parseScalaFile method.
           |""".stripMargin,
      )
      features
    case _ =>
      scalaCodeParsingFeatures
  }

  def parseScalaFileAndGetCaretPosition(
    @InputLanguage("Scala") text: String,
    caretMarker: String
  )(implicit project: ProjectContext): (ScalaFile, Int) = {
    val trimmed = text.trim
    val caretPos = trimmed.indexOf(caretMarker)
    (parseScalaFile(trimmed.replaceAll(caretMarker, "")), caretPos)
  }

  def parseScalaFile(
    @InputLanguage("Scala") text: String,
    scalaFeatures: ScalaFeatures = checkedCodeParsingFeatures,
    enableEventSystem: Boolean = false
  )(implicit project: ProjectContext): ScalaFile = {
    ScalaPsiElementFactory.createScalaFileFromText(
      text,
      scalaFeatures,
      eventSystemEnabled = enableEventSystem,
      shouldTrimText = false
    )
  }

  implicit class ScalaCode(@InputLanguage("Scala") private val text: String) {
    def stripComments: String =
      text.replaceAll("""(?s)/\*.*?\*/""", "")
        .replaceAll("""(?m)//.*$""", "")

    def parse()(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text)

    def parse(parsingFeatures: ScalaFeatures)(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text, parsingFeatures)

    def parseWithEventSystem(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text, enableEventSystem = true)

    def parse[T <: PsiElement : ClassTag](implicit project: ProjectContext): T =
      parse()(project).depthFirst().findByType[T].getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " +
          implicitly[ClassTag[T]].runtimeClass.getSimpleName)
      }
  }
}
