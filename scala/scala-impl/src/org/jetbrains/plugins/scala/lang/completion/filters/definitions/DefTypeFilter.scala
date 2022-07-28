package org.jetbrains.plugins.scala
package lang
package completion
package filters
package definitions

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

class DefTypeFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScReferenceExpression =>
        case file: ScalaFile if file.isScala3File =>
          return awful(parent, leaf)
        case _ => return false
      }
      parent.getParent match {
        case parent@(_: ScDeclarationSequenceHolder |
                     _: ScCaseClause |
                     _: ScTemplateBody |
                     _: ScClassParameter) =>
          if (awful(parent, leaf))
            return true
        case _ =>
          return checkAfterSoftModifier(parent, leaf)
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "'def', 'type' keyword filter"
  }
}