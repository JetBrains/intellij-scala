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

  protected def scalaVersion: ScalaVersion = ScalaVersion.default

  def parseScalaFile(
    @InputLanguage("Scala") text: String,
    scalaVersion: ScalaVersion
  )(implicit project: ProjectContext): ScalaFile = {
    parseScalaFile(text, scalaVersion, enableEventSystem = false)
  }

  def parseScalaFile(
    @InputLanguage("Scala") text: String,
    enableEventSystem: Boolean = false
  )(implicit project: ProjectContext): ScalaFile = {
    parseScalaFile(text, scalaVersion, enableEventSystem)
  }

  def parseScalaFileAndGetCaretPosition(
    @InputLanguage("Scala") text: String,
    caretMarker: String
  )(implicit project: ProjectContext): (ScalaFile, Int) = {
    val trimmed = text.trim
    val caretPos = trimmed.indexOf(caretMarker)
    (parseScalaFile(trimmed.replaceAll(caretMarker, "")), caretPos)
  }

  private def parseScalaFile(
    @InputLanguage("Scala") text: String,
    scalaVersion: ScalaVersion,
    enableEventSystem: Boolean,
  )(implicit project: ProjectContext): ScalaFile = {
    val scalaFeatures = ScalaFeatures.onlyByVersion(scalaVersion)
    ScalaPsiElementFactory.createScalaFileFromText(text, scalaFeatures, eventSystemEnabled = enableEventSystem, shouldTrimText = false)
  }

  implicit class ScalaCode(@InputLanguage("Scala") private val text: String) {
    def stripComments: String =
      text.replaceAll("""(?s)/\*.*?\*/""", "")
        .replaceAll("""(?m)//.*$""", "")

    def parse(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text)

    def parse(scalaVersion: ScalaVersion)(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text, scalaVersion)

    def parseWithEventSystem(implicit project: ProjectContext): ScalaFile =
      parseScalaFile(text, enableEventSystem = true)

    def parse[T <: PsiElement : ClassTag](implicit project: ProjectContext): T =
      parse(project).depthFirst().findByType[T].getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " +
          implicitly[ClassTag[T]].runtimeClass.getSimpleName)
      }
  }
}
