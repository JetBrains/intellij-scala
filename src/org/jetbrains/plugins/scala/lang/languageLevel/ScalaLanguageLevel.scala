package org.jetbrains.plugins.scala
package lang.languageLevel

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.config.ScalaFacet

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 24.10.12
 */
object ScalaLanguageLevel extends Enumeration {
  type ScalaLanguageLevel = ScalaLanguageLevel.Value

  val SCALA2_9 = Value("Scala 2.9")
  val SCALA2_10 = Value("Scala 2.10")
  val SCALA2_10_VIRTUALIZED = Value("Scala 2.10 virtualized")
  val SCALA2_11 = Value("Scala 2.11")
  val SCALA2_11_VIRTUALIZED = Value("Scala 2.11 virtualized")

  val DEFAULT_LANGUAGE_LEVEL = SCALA2_10

  def isVirtualized(languageLevel: ScalaLanguageLevel): Boolean = {
    languageLevel match {
      case SCALA2_10_VIRTUALIZED | SCALA2_11_VIRTUALIZED => true
      case _ => false
    }
  }

  def isThoughScala2_10(languageLevel: ScalaLanguageLevel): Boolean = {
    languageLevel match {
      case SCALA2_10 | SCALA2_10_VIRTUALIZED | SCALA2_11 | SCALA2_11_VIRTUALIZED => true
      case _ => false
    }
  }

  def isThoughScala2_11(languageLevel: ScalaLanguageLevel): Boolean = {
    languageLevel match {
      case SCALA2_11 | SCALA2_11_VIRTUALIZED => true
      case _ => false
    }
  }

  def valuesArray(): Array[ScalaLanguageLevel] = values.toArray

  implicit class ValueImpl(val languageLevel: ScalaLanguageLevel) extends AnyVal {
    def isVirtualized: Boolean = {
      languageLevel match {
        case SCALA2_10_VIRTUALIZED => true
        case SCALA2_11_VIRTUALIZED => true
        case _ => false
      }
    }

    def isThoughScala2_10: Boolean = {
      languageLevel match {
        case SCALA2_10 | SCALA2_10_VIRTUALIZED | SCALA2_11 | SCALA2_11_VIRTUALIZED => true
        case _ => false
      }
    }

    def isThoughScala2_11: Boolean = {
      languageLevel match {
        case SCALA2_11 | SCALA2_11_VIRTUALIZED => true
        case _ => false
      }
    }
  }

  def getLanguageLevel(element: PsiElement): ScalaLanguageLevel = {
    @tailrec
    def getContainingFileByContext(element: PsiElement): PsiFile = {
      element match {
        case file: PsiFile => file
        case null => null
        case elem => getContainingFileByContext(elem.getContext)
      }
    }
    val file: PsiFile = getContainingFileByContext(element)
    if (file == null || file.getVirtualFile == null) return DEFAULT_LANGUAGE_LEVEL
    val module: Module = ProjectFileIndex.SERVICE.getInstance(element.getProject).getModuleForFile(file.getVirtualFile)
    if (module == null) return DEFAULT_LANGUAGE_LEVEL
    ScalaFacet.findIn(module).map(_.languageLevel).getOrElse(DEFAULT_LANGUAGE_LEVEL)
  }
}
