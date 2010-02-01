package org.jetbrains.plugins.scala.annotator.gutter

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScClass}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackageContainer
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScIfStmt, ScBlock, ScNewTemplateDefinition}
import com.intellij.psi.{PsiComment, PsiWhiteSpace, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * Pavel.Fatin, 20.01.2010
 */
trait ScalaSeparatorProvider {
  val DEFAULT_GROUP = 0
  val MULTILINE_LEVEL = 10

  //TODO remove commments handling when SCL-1751 will be fixed
  def isSeparatorNeeded(element: PsiElement): Boolean = {
    if (isKnown(element) && !hasUpperComment(element)) {
      if (element.isInstanceOf[PsiComment]) {
        if (!isLineTail(element)) {
          val e = getCommentedElement(element)
          if (e.isDefined) doIfSeparatorNeeded(e.get) else false
        } else false
      } else {
        doIfSeparatorNeeded(element)
      }
    } else false
  }

  def isKnown(element: PsiElement) = {
    element.isInstanceOf[PsiComment] || groupOf(element).isDefined
  }

  def getCommentedElement(element: PsiElement): Option[PsiElement] = {
    var lines = 0
    var e = element.getNextSibling
    while (e != null) {
      val count = getNewLinesCount(e)
      if (count > 0) {
        lines += count
        if (count > 1) {
          return None
        }
      } else {
        if (getGroup(e).isDefined) {
          return if (lines > 0) Some(e) else None
        }
      }
      e = e.getNextSibling
    }
    None
  }

  def hasUpperComment(element: PsiElement): Boolean = {
    var lines = 0
    var e = element.getPrevSibling
    while (e != null) {
      val count = getNewLinesCount(e)
      if (count > 0) {
        lines += count
      } else {
        return lines == 1 && e.isInstanceOf[PsiComment] && !isLineTail(e)
      }
      e = e.getPrevSibling
    }
    false
  }

  def isLineTail(element: PsiElement): Boolean = {
    var e = element.getPrevSibling
    while (e != null) {
      if (groupOf(e).isDefined) return true
      if (getNewLinesCount(e) > 0) return false
      e = e.getPrevSibling
    }
    false
  }

  def getNewLinesCount(element: PsiElement) = {
    val text = element.getText
    if (element.isInstanceOf[PsiWhiteSpace] || element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR)
      text.count(_ == '\n')
    else 0
  }

  def doIfSeparatorNeeded(element: PsiElement) = {
    if (isSeparationContainer(element.getParent) && hasElementAbove(element)) {
      val g = getGroup(element)
      if (g.get >= MULTILINE_LEVEL) {
        true
      } else {
        g != getGroupAbove(element) {_ => true}
      }
    } else false
  }

  def hasElementAbove(element: PsiElement) = {
    getGroupAbove(element) {!_.isInstanceOf[ScImportStmt]}.isDefined
  }

  def getGroup(element: PsiElement) = {
    for (g <- groupOf(element))
    yield if (isMultiline(element)) MULTILINE_LEVEL + g else g
  }

  def groupOf(element: PsiElement): Option[Int] = {
    element match {
      case _: ScValue |
              _: ScVariable |
              _: ScTypeAlias |
              _: ScFunction |
              _: ScImportStmt |
              _: ScPackageContainer |
              _: ScClass |
              _: ScObject |
              _: ScTrait |
              _: ScBlock => Some(DEFAULT_GROUP)
      case it: ScNewTemplateDefinition if (it.extendsBlock != null) => Some(DEFAULT_GROUP)
      case _ => None
    }
  }

  //TODO remove ".trim" when SCL-1746 will be fixed
  def isMultiline(element: PsiElement) = {
    hasUpperComment(element) || element.getText.trim.contains('\n') // trim to bypass SCL-1746
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

  def isSeparationBlocker(element: PsiElement) = {
    element match {
      case _: ScBlock | _: ScIfStmt => true
      case it: ScNewTemplateDefinition if(it.extendsBlock != null) => true
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
        lines += e.getText.count(_ == '\n')
      }
      e = e.getPrevSibling
    }
    None
  }
}
