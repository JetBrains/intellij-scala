package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.settings._

/**
 * @author Alefas
 * @since 28.03.12
 */
class ScalaClassesCompletionWeigher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    if (!ScalaProjectSettings.getInstance(location.getProject).isScalaPriority) return null
    ScalaLookupItem.original(element) match {
      case s: ScalaLookupItem =>
        s.element match {
          case s: ScTypeDefinition => 1
          case c: PsiClass => 0
          case _ => null
        }
      case _ => null
    }
  }
}
