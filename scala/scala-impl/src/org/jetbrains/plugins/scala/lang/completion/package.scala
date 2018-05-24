package org.jetbrains.plugins.scala
package lang

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.getDummyIdentifier
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner

import scala.annotation.tailrec
import scala.collection.JavaConverters

package object completion {

  def positionFromParameters(parameters: CompletionParameters): PsiElement = {
    @tailrec
    def position(element: PsiElement): PsiElement = element match {
      case null => parameters.getPosition // we got to the top of the tree and didn't find a modificationTrackerOwner
      case owner@ScModificationTrackerOwner() =>
        val maybeMirrorPosition = parameters.getOriginalFile match {
          case file if owner.containingFile.contains(file) =>
            val offset = parameters.getOffset
            val dummyId = getDummyIdentifier(offset, file)
            owner.mirrorPosition(dummyId, offset)
          case _ => None
        }

        maybeMirrorPosition.getOrElse(parameters.getPosition)
      case _ => position(element.getContext)
    }

    position(parameters.getOriginalPosition)
  }

  abstract class ScalaCompletionProvider extends CompletionProvider[CompletionParameters] {

    protected def completionsFor(position: PsiElement)
                                (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[ScalaLookupItem]

    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val items = completionsFor(positionFromParameters(parameters))(parameters, context)

      import JavaConverters._
      result.addAllElements(items.asJava)
    }
  }
}
