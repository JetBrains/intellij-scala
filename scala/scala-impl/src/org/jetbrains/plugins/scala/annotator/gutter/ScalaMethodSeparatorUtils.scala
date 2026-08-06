package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScIf, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}

//noinspection InstanceOf
object ScalaMethodSeparatorUtils {
  private val DefaultGroup = 0
  private val MultilineLevel = 10

  def isMethodSeparatorNeeded(element: PsiElement): Boolean = {
    isKnown(element) && doIfSeparatorNeeded(element)
  }

  private def isKnown(element: PsiElement): Boolean = {
    val group = groupOf(element)
    group.isDefined
  }

  private def doIfSeparatorNeeded(element: PsiElement): Boolean = {
    if (isSeparationContainer(element.getParent) && hasElementAbove(element)) {
      val group = getGroup(element)
      if (group.get >= MultilineLevel)
        true
      else {
        val groupAbove = getGroupAbove(element)(_ => true)
        group != groupAbove
      }
    }
    else false
  }

  private def hasElementAbove(element: PsiElement): Boolean = {
    val group = getGroupAbove(element)(!_.isInstanceOf[ScImportStmt])
    group.isDefined
  }

  private def getGroup(element: PsiElement): Option[Int] = {
    val groupOpt = groupOf(element)
    for (group <- groupOpt) yield {
      if (isMultiline(element)) MultilineLevel + group
      else group
    }
  }

  private def groupOf(element: PsiElement): Option[Int] = {
    element match {
      case _: ScValue |
           _: ScVariable |
           _: ScTypeAlias |
           _: ScFunction |
           _: ScImportStmt |
           _: ScPackaging |
           _: ScClass |
           _: ScObject |
           _: ScTrait |
           _: ScBlock =>
        Some(DefaultGroup)
      case it: ScNewTemplateDefinition if it.extendsBlock != null =>
        Some(DefaultGroup)
      case _ =>
        None
    }
  }

  private def isMultiline(element: PsiElement): Boolean =
    element.textContains('\n')

  private def isSeparationContainer(element: PsiElement): Boolean = {
    element.getContainingFile match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile =>
        return false
      case _ =>
    }

    var e = element
    while (e != null) {
      if (isSeparationBlocker(e)) {
        return false
      }
      e = e.getParent
    }
    true
  }

  private def isSeparationBlocker(element: PsiElement): Boolean = {
    element match {
      case _: ScBlock | _: ScIf => true
      case it: ScNewTemplateDefinition if it.extendsBlock != null => true
      case _ => false
    }
  }

  private def getGroupAbove(element: PsiElement)(filter: PsiElement => Boolean): Option[Int] = {
    var lines = 0
    var e = element.getPrevSibling
    while (e != null) {
      val g = getGroup(e)
      if (g.isDefined) {
        if (lines > 0 && filter(e))
          return g
      } else {
        lines += augmentString(e.getText).count(_ == '\n')
      }
      e = e.getPrevSibling
    }
    None
  }
}