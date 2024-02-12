package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.toplevel.IsTopLevelElementInProductionScalaFileFilter
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

/** Also see [[org.jetbrains.plugins.scala.lang.completion.filters.expression.QuietCatchCaseFilter]] */
class CaseFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf.getParent != null) {
      val parent =
        if (CaseFilter.isToplevelLeaf(leaf)) leaf.getParent.getParent
        else leaf.getParent
      parent match {
        case _: ScalaFile =>
          if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.is[ScPackaging] &&
            leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
            return false
        case _ =>
      }
      parent match {
        case _: ScCaseClause =>
          if (parent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
          else return false
        case _: ScMatch => return true
        case _: ScalaFile | _: ScPackaging =>
          var node = leaf.getPrevSibling
          if (node.is[PsiWhiteSpace]) node = node.getPrevSibling
          node match {
            case x: PsiErrorElement =>
              val s = ErrMsg("wrong.top.statement.declaration")
              x.getErrorDescription match {
                case `s` => return true
                case _ => return false
              }
            case _ => return true
          }
        case _ =>
      }
      if (parent.getParent != null) {
        parent.getParent.getParent match {
          case _: ScCaseClause =>
            if (parent.getParent.getParent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
            else return false
          case _ =>
        }
      }
      parent.getParent match {
        case _: ScBlockExpr | _: ScTemplateBody =>
          parent match {
            case _: ScReferenceExpression =>
            case _ => return false
          }
          if (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF)
            return true
        case _: ScCaseClause =>
          if (parent.getParent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
          else return false
        case _ if checkAfterSoftModifier(parent, leaf) => return true
        case _ =>
      }
      if (leaf.getPrevSibling != null &&
              leaf.getPrevSibling.getPrevSibling != null &&
        ((leaf.getPrevSibling.getPrevSibling.getNode.getElementType == ScalaElementType.MATCH_STMT &&
                      leaf.getPrevSibling.getPrevSibling.getLastChild.is[PsiErrorElement]) ||
          (leaf.getPrevSibling.getPrevSibling.getNode.getElementType == ScalaElementType.TRY_STMT &&
                leaf.getPrevSibling.getPrevSibling.getLastChild.is[ScCatchBlock] &&
                       leaf.getPrevSibling.getPrevSibling.getLastChild.getLastChild.is[PsiErrorElement]))
              )
        return true
      if (parent.is[ScTemplateBody]) {
        if (leaf.getPrevSibling != null &&
            leaf.getPrevSibling.getPrevSibling != null &&
            leaf.getPrevSibling.getPrevSibling.getLastChild != null &&
          leaf.getPrevSibling.getPrevSibling.getLastChild.getNode.getElementType == ScalaElementType.MATCH_STMT &&
            leaf.getPrevSibling.getPrevSibling.getLastChild.getText.indexOf('{') != -1 &&
            leaf.getPrevSibling.getPrevSibling.getLastChild.getLastChild.is[PsiErrorElement])
          return true
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'case' keyword filter"
}

object CaseFilter {
  private def isToplevelLeaf(leaf: PsiElement) = leaf.getParent match {
    case ref: ScReferenceExpression =>
      IsTopLevelElementInProductionScalaFileFilter.check(ref)
    case _ => false
  }
}
