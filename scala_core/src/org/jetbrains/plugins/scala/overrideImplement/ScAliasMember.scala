package org.jetbrains.plugins.scala.overrideImplement

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import com.intellij.psi.{PsiMethod, PsiSubstitutor}
import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.ClassMember
import lang.psi.api.statements.{ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScTyped
/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

class ScAliasMember(member: ScTypeAlias, substitutor: ScSubstitutor) extends PsiElementClassMember[ScTypeAlias](member, member.name)

class ScMethodMember(val sign: PhysicalSignature) extends PsiElementClassMember[PsiMethod](sign.method, sign.method.getName)

class ScValueMember(member: ScValue, val element: ScTyped, val substitutor: ScSubstitutor) extends PsiElementClassMember[ScValue](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.calcType)))

class ScVariableMember(member: ScVariable, val element: ScTyped, val substitutor: ScSubstitutor) extends PsiElementClassMember[ScVariable](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.calcType)))