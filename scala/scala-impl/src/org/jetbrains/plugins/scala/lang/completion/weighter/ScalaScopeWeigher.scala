package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
  * Created by kate
  * on 2/17/16
  */
class ScalaScopeWeigher extends CompletionWeigher {
  def computeLevelsBetween(it: Iterator[PsiElement], y: PsiElement): Option[Int] = {
    val idx = it.indexOf(y)
    if (idx == -1) None else Some(-idx)
  }

  def checkByContext(first: PsiElement, second: PsiElement): Option[Int] = {
    if (PsiTreeUtil.isContextAncestor(second, first, true))
      computeLevelsBetween(first.contexts, second)
    else None
  }

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val completionPosition = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)

    ScalaLookupItem.original(element) match {
      case sl: ScalaLookupItem =>
        if (sl.element.scopes.hasNext) {
          checkByContext(completionPosition, sl.element.scopes.next()) match {
            case Some(value) => value
            case _ => null
          }
        }
        else null
      case _ => null
    }
  }
}