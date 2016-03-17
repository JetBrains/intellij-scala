package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

trait ScalaNamedMember {
  def name: String
}

trait ScalaTypedMember {
  def scType: ScType
}

class ScAliasMember(member: ScTypeAlias, val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          val name: String = member.name
        } with PsiElementClassMember[ScTypeAlias](member, name) with ScalaNamedMember

class ScMethodMember(val sign: PhysicalSignature, val isOverride: Boolean)
        extends {
          val name: String = sign.name
          val scType: ScType = sign.method match {
            case fun: ScFunction => sign.substitutor.subst(fun.returnType.getOrAny)
            case method: PsiMethod =>
              sign.substitutor.subst(Option(method.getReturnType).getOrElse(PsiType.VOID)
                .toScType(method.getProject, method.getResolveScope))
          }
          val text = ScalaPsiUtil.getMethodPresentableText(sign.method)
        } with PsiElementClassMember[PsiMethod](sign.method, text) with ScalaNamedMember with ScalaTypedMember

class ScValueMember(member: ScValue, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          val name = element.getName
          val scType = substitutor.subst(element.getType(TypingContext.empty).getOrAny)
          val text = element.name + ": " + ScType.presentableText(scType)
        } with PsiElementClassMember[ScValue](member, text) with ScalaNamedMember with ScalaTypedMember

class ScVariableMember(member: ScVariable, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          val name = element.getName
          val scType = substitutor.subst(element.getType(TypingContext.empty).getOrAny)
          val text = name + ": " + ScType.presentableText(scType)
        } with PsiElementClassMember[ScVariable](member, text) with ScalaNamedMember with ScalaTypedMember

class JavaFieldMember(field: PsiField, val substitutor: ScSubstitutor)
        extends {
          val scType = substitutor.subst(field.getType.toScType(field.getProject, field.getResolveScope))
          val name = field.getName
          val text = name + ": " + ScType.presentableText(scType)
        } with PsiElementClassMember[PsiField](field, text) with ScalaNamedMember with ScalaTypedMember