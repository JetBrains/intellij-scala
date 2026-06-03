package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractCopyFQNCompletionCommandProvider
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

//noinspection UnstableApiUsage
final class ScalaCopyFQNCompletionCommandProvider extends AbstractCopyFQNCompletionCommandProvider {
  override def placeIsApplicable(element: PsiElement, offset: Int): Boolean =
    if (element == null || element.elementType != ScalaTokenTypes.tIDENTIFIER) false
    else element.parentOfType(Seq(classOf[ScReference], classOf[ScNamedElement])) match {
      case Some(ref: ScReference) => isApplicable(ref, ref, offset)
      case Some(named: ScNamedElement) => isApplicable(named, named.nameId, offset)
      case _ => false
    }

  private def isApplicable(@NotNull element: PsiElement, @Nullable anchor: PsiElement, offset: Int): Boolean =
    anchor != null && anchor.getTextRange.containsOffset(offset) && CopyReferenceAction.elementToFqn(element) != null
}
