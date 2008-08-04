package org.jetbrains.plugins.scala.overrideImplement

import lang.psi.api.toplevel.ScTyped
import com.intellij.codeInsight.generation.PsiElementClassMember
import lang.psi.api.statements.ScValue

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.07.2008
 */

class PsiValueMember(member: ScValue, val element: ScTyped) extends PsiElementClassMember[ScValue](member,
  element.name/*format with type*/) {

}