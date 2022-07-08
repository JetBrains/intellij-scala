package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.other.ExtendsFilter._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

class ExtendsFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    if (leaf == null) return false

    // class Test exten
    //           ^ find error here
    val errorBeforeExtendsStart = leaf.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

    errorBeforeExtendsStart match {
      case Some((_: PsiErrorElement) && PrevSibling(typeDefBeforeError: ScTypeDefinition)) =>
        !hasTemplateParents(typeDefBeforeError) &&
          // do not suggest if there is already an extends. i.e.:
          // class Test e<caret> extends
          !afterExtends(leaf)
      case Some((_: PsiErrorElement) && PrevSibling(cases: ScEnumCases)) =>
        !cases.declaredElements.exists(hasTemplateParents) && !afterExtends(leaf)
      case _ =>
        false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'extends' keyword filter"
}

object ExtendsFilter {
  private def hasTemplateParents(td: ScTypeDefinition): Boolean = td.extendsBlock.templateParents.isDefined

  private def afterExtends(leaf: PsiElement): Boolean =
    leaf.nextVisibleLeaf.exists(_.elementType == ScalaTokenTypes.kEXTENDS)
}
