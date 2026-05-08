package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractGoToImplementationCompletionCommandProvider
import com.intellij.psi.search.searches.{ClassInheritorsSearch, ReferencesSearch}
import com.intellij.psi.{LambdaUtil, PsiElement}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//noinspection UnstableApiUsage
final class ScalaGoToImplementationCompletionCommandProvider extends AbstractGoToImplementationCompletionCommandProvider {
  override def canGoToImplementation(element: PsiElement, offset: Int): Boolean = {
    if (element == null || element.elementType != ScalaTokenTypes.tIDENTIFIER) return false

    val maybeClass = element.getParent match {
      case td: ScTypeDefinition if offsetIsAtName(td, offset) => Some(td)
      case fn: ScFunction if offsetIsAtName(fn, offset) && !fn.hasFinalModifier => Option(fn.containingClass)
      case _ => None
    }

    !maybeClass.forall { cls =>
      cls.hasFinalModifier ||
        ClassInheritorsSearch.search(cls, false).findFirst() == null &&
          !(LambdaUtil.isFunctionalClass(cls) &&
            ReferencesSearch.search(cls).findFirst() != null)
    }
  }

  private def offsetIsAtName(element: ScNamedElement, offset: Int): Boolean = {
    val nameId = element.nameId
    nameId != null && nameId.getTextRange.containsOffset(offset)
  }
}
