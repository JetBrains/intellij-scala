package org.jetbrains.plugins.scala
package codeInsight.template.util

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */
class VariablesCompletionProcessor(override val kinds: Set[ResolveTargets.Value])
                                  (implicit override val typeSystem: TypeSystem) extends BaseProcessor(kinds) {
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (kindMatches(element)) {
      candidatesSet += new ScalaResolveResult(named)
    }
    true
  }
}