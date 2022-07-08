package org.jetbrains.plugins.scala.lang
package completion
package weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

class ScalaScopeWeigher extends CompletionWeigher {

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = element match {
    case ScalaLookupItem(_, namedElement) =>
      val scopes = namedElement.scopes
      if (scopes.hasNext) checkByContext(positionFromParameters(location.getCompletionParameters), scopes.next())
      else null
    case _ => null
  }

  private def checkByContext(first: PsiElement, second: PsiElement): Integer = {
    if (PsiTreeUtil.isContextAncestor(second, first, true))
      first.contexts.indexOf(second) match {
        case -1 => null
        case index => -index
      }
    else null
  }
}