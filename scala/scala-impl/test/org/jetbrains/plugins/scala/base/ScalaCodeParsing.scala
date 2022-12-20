package org.jetbrains.plugins.scala.base

import com.intellij.lang.Language
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.intellij.lang.annotations.{Language => InputLanguage}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.reflect.ClassTag

trait ScalaCodeParsing {
  def parseText(@InputLanguage("Scala") s: String, lang: Language)
               (implicit project: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", lang, s)
      .asInstanceOf[ScalaFile]

  def parseText(@InputLanguage("Scala") s: String, enableEventSystem: Boolean = false)
               (implicit project: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s, System.currentTimeMillis(), enableEventSystem)
      .asInstanceOf[ScalaFile]

  def parseText(@InputLanguage("Scala") s: String, caretMarker: String)
               (implicit project: ProjectContext): (ScalaFile, Int) = {
    val trimmed = s.trim
    val caretPos = trimmed.indexOf(caretMarker)
    (parseText(trimmed.replaceAll(caretMarker, "")), caretPos)
  }

  implicit class ScalaCode(@InputLanguage("Scala") private val s: String) {
    def stripComments: String =
      s.replaceAll("""(?s)/\*.*?\*/""", "")
        .replaceAll("""(?m)//.*$""", "")

    def parse(implicit project: ProjectContext): ScalaFile = parseText(s)

    def parse(lang: Language)(implicit project: ProjectContext): ScalaFile = parseText(s, lang)

    def parseWithEventSystem(implicit project: ProjectContext): ScalaFile = parseText(s, enableEventSystem = true)

    def parse[T <: PsiElement : ClassTag](implicit project: ProjectContext): T =
      parse(project).depthFirst().findByType[T].getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " +
          implicitly[ClassTag[T]].runtimeClass.getSimpleName)
      }
  }
}
