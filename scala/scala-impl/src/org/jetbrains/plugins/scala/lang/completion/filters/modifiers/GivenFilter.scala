package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers.GivenFilter._
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class GivenFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf != null) {
      val parent = leaf.getParent
      val (stopHere, res) = getForAll(parent, leaf)

      if (stopHere) return res
      if (checkAfterSoftModifier(parent, leaf)) return true

      parent match {
        case _: ScReferenceExpression =>
          val context = if (parent.getParent.is[ScTuple]) parent.getParent else parent
          checkErrorInFor(context)
        case _: ScStableCodeReference =>
          parent.parentOfType[ScAnnotation] match {
            case Some(annotation & PrevSiblingNotWhitespace(_: ScReferenceExpression | _: ScTuple)) =>
              checkErrorInFor(annotation.getParent)
            case _ => false
          }
        case pat: ScReferencePattern =>
          var element = pat.getParent
          while (element != null && !element.is[ScFor, ScCaseClause]) {
            val isValid = element.is[ScInfixPattern, ScNamingPattern, ScTuplePattern, ScPatterns] ||
              element.is[ScForBinding, ScGenerator, ScEnumerators]

            if (!isValid) return false
            element = element.getParent
          }
          element != null
        case _ => false
      }
    } else false
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "given keyword filter"
}

object GivenFilter {
  private def checkErrorInFor(context: PsiElement): Boolean = {
    val errorBeforeGiven = context.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

    errorBeforeGiven match {
      case Some(err: PsiErrorElement) => err.getErrorDescription == ErrMsg("expected.do.or.yield")
      case _ => false
    }
  }
}
