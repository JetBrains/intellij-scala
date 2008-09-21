package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.psi.{PsiMethod, PsiSubstitutor}
import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.ClassMember
import lang.psi.api.statements.{ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScTyped
import lang.psi.types.PhysicalSignature

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

class ScAliasMember(member: ScTypeAlias) extends PsiElementClassMember[ScTypeAlias](member, member.name)

class ScMethodMember(val sign: PhysicalSignature) extends PsiElementClassMember[PsiMethod](sign.method, sign.method.getName)

class ScValueMember(member: ScValue, val element: ScTyped) extends PsiElementClassMember[ScValue](member,
  element.name/*format with type*/)

class ScVariableMember(member: ScVariable, val element: ScTyped) extends PsiElementClassMember[ScVariable](member,
  element.name/*format with type*/)