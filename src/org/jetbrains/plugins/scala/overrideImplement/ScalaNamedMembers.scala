package org.jetbrains.plugins.scala
package overrideImplement

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import com.intellij.psi.{PsiType, PsiClass, PsiMethod, PsiSubstitutor}
import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.ScalaPsiUtil
import lang.psi.types._
import lang.psi.types.result.TypingContext

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

private[overrideImplement] trait ScalaNamedMembers {
  def name: String
}

class ScAliasMember(member: ScTypeAlias, val substitutor: ScSubstitutor, val isImplement: Boolean)
  extends PsiElementClassMember[ScTypeAlias](member, member.name) with ScalaNamedMembers {

  val name: String = member.name
}

class ScMethodMember(val sign: PhysicalSignature, val isImplement: Boolean) extends PsiElementClassMember[PsiMethod](sign.method,
  ScalaPsiUtil.getMethodPresentableText(sign.method)) with ScalaNamedMembers {

  val name: String = sign.name

  val returnType: ScType = sign.method match {
    case fun: ScFunction => sign.substitutor.subst(fun.returnType.getOrAny)
    case method: PsiMethod =>
      sign.substitutor.subst(ScType.create(Option(method.getReturnType).getOrElse(PsiType.VOID),
        method.getProject, method.getResolveScope
      ))
  }
}

class ScValueMember(member: ScValue, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isImplement: Boolean) extends PsiElementClassMember[ScValue](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.getType(TypingContext.empty).getOrAny))) with ScalaNamedMembers {

  val name: String = element.name
}

class ScVariableMember(member: ScVariable, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isImplement: Boolean) extends PsiElementClassMember[ScVariable](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.getType(TypingContext.empty).getOrAny))) with ScalaNamedMembers {
 
  val name: String = element.name
}