package org.jetbrains.plugins.scala.overrideImplement

import lang.psi.api.toplevel.ScTyped
import com.intellij.codeInsight.generation.PsiElementClassMember
import lang.psi.api.statements.ScVariable

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.07.2008
 */

class PsiVariableMember(member: ScVariable, val element: ScTyped) extends PsiElementClassMember[ScVariable](member,
  element.name/*format with type*/) {

}