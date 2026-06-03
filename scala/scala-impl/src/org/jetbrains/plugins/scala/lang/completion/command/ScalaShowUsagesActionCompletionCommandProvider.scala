package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractShowUsagesActionCompletionCommandProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

//noinspection UnstableApiUsage
final class ScalaShowUsagesActionCompletionCommandProvider extends AbstractShowUsagesActionCompletionCommandProvider {
  override def hasToShow(element: PsiElement): Boolean =
    element != null &&
      element.elementType == ScalaTokenTypes.tIDENTIFIER &&
      element.parent.exists {
        case ref: ScReference => ref.nameId == element
        case named: ScNamedElement => named.nameId == element
        case _ => false
      }
}
