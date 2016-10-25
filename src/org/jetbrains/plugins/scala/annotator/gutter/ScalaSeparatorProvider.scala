package org.jetbrains.plugins.scala
package annotator.gutter

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScIfStmt, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}

/**
 * Pavel.Fatin, 20.01.2010
 */
trait ScalaSeparatorProvider {
  val DefaultGroup = 0
  val MultilineLevel = 10

  def isSeparatorNeeded(element: PsiElement): Boolean = {
    isKnown(element) && doIfSeparatorNeeded(element)
  }

  def isKnown(element: PsiElement): Boolean = groupOf(element).isDefined

  def doIfSeparatorNeeded(element: PsiElement): Boolean = {
    if (isSeparationContainer(element.getParent) && hasElementAbove(element)) {
      val g = getGroup(element)
      if (g.get >= MultilineLevel) {
        true
      } else {
        g != getGroupAbove(element) {_ => true}
      }
    } else false
  }

  def hasElementAbove(element: PsiElement): Boolean = {
    getGroupAbove(element) {!_.isInstanceOf[ScImportStmt]}.isDefined
  }

  def getGroup(element: PsiElement): Option[Int] = {
    for (g <- groupOf(element))
    yield if (isMultiline(element)) MultilineLevel + g else g
  }

  def groupOf(element: PsiElement): Option[Int] = {
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
           _: ScBlock => Some(DefaultGroup)
      case it: ScNewTemplateDefinition if it.extendsBlock != null => Some(DefaultGroup)
      case _ => None
    }
  }

  //TODO remove ".trim" when SCL-1746 will be fixed
  def isMultiline(element: PsiElement): Boolean = {
    element.getText.trim.contains('\n') // trim to bypass SCL-1746
  }

  def isSeparationContainer(element: PsiElement): Boolean = {
    var e = element
    while (e != null) {
      if (isSeparationBlocker(e)) {
        return false
      }
      e = e.getParent
    }
    true
  }

  def isSeparationBlocker(element: PsiElement): Boolean = {
    element match {
      case _: ScBlock | _: ScIfStmt => true
      case it: ScNewTemplateDefinition if it.extendsBlock != null => true
      case _ => false
    }
  }

  def getGroupAbove(element: PsiElement)(filter: PsiElement => Boolean): Option[Int] = {
    var lines = 0
    var e = element.getPrevSibling
    while (e != null) {
      val g = getGroup(e)
      if (g.isDefined) {
        if (lines > 0 && filter(e)) return g
      } else {
        lines += augmentString(e.getText).count(_ == '\n')
      }
      e = e.getPrevSibling
    }
    None
  }
}
