package org.jetbrains.plugins.scala
package lang

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.getDummyIdentifier
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner

import scala.annotation.tailrec

package object completion {

  def positionFromParameters(implicit parameters: CompletionParameters): PsiElement = {
    @tailrec
    def position(element: PsiElement): PsiElement = element match {
      case null => parameters.getPosition // we got to the top of the tree and didn't find a modificationTrackerOwner
      case owner: ScModificationTrackerOwner if owner.isValidModificationTrackerOwner =>
        val maybeMirrorPosition = parameters.getOriginalFile match {
          case file if owner.containingFile.contains(file) =>
            val offset = parameters.getOffset
            val dummyId = getDummyIdentifier(offset, file)
            owner.getMirrorPositionForCompletion(dummyId, offset - owner.getTextRange.getStartOffset)
          case _ => None
        }

        maybeMirrorPosition.getOrElse(parameters.getPosition)
      case _ => position(element.getContext)
    }

    position(parameters.getOriginalPosition)
  }

}
