package org.jetbrains.plugins.scala.lang.completion.filters.other

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.isAfterEmptyLine
import org.jetbrains.plugins.scala.lang.completion.filters.other.ExtendsFilter._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

final class ExtendsFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    if (
      // do not suggest if there is already an `extends` keyword. e.g.:
      // class Test e<caret> extends
      isBeforeExtends(leaf) ||
      isAfterEmptyLine(leaf)) return false

    // class Test exten
    //           ^ find error here
    // there will be no error if `extends` is on new line
    val maybeErrorBeforeExtendsStart = leaf.prevLeafs
      .dropWhile(_.is[PsiComment, PsiWhiteSpace])
      .nextOption()

    maybeErrorBeforeExtendsStart match {
      case Some(error: PsiErrorElement) =>
        error.prevSibling
          .exists(checkExtends)
      case Some(_) =>
        leaf.parentOfType[ScReference]
          .flatMap(_.prevSiblingNotWhitespaceComment)
          .exists(checkExtends)
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'extends' keyword filter"
}

object ExtendsFilter {
  private def checkExtends(element: PsiElement): Boolean =
    element match {
      case td: ScTypeDefinition =>
        !hasTemplateParents(td)
      case cases: ScEnumCases =>
        !cases.declaredElements.exists(hasTemplateParents)
      case _ => false
    }

  private def hasTemplateParents(td: ScTypeDefinition): Boolean =
    td.extendsBlock.templateParents.isDefined

  private def isBeforeExtends(leaf: PsiElement): Boolean =
    leaf.nextVisibleLeaf(skipComments = true)
      .exists(_.elementType == ScalaTokenTypes.kEXTENDS)
}
