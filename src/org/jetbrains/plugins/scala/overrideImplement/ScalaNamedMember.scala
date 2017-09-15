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

case class ScAliasMember(override val getElement: ScTypeAlias,
                         substitutor: ScSubstitutor,
                         isOverride: Boolean)
  extends PsiElementClassMember[ScTypeAlias](getElement, getElement.name) with ScalaNamedMember {

  override val name: String = getText
}

class ScMethodMember(val sign: PhysicalSignature, val isOverride: Boolean)
        extends {
          val name: String = sign.name
          val scType: ScType = sign.method match {
            case fun: ScFunction => sign.substitutor.subst(fun.returnType.getOrAny)
            case method: PsiMethod =>
              val psiType = Option(method.getReturnType).getOrElse(PsiType.VOID)
              val fromPsiType = psiType.toScType()(sign.projectContext)
              sign.substitutor.subst(fromPsiType)
          }
          val text = ScalaPsiUtil.getMethodPresentableText(sign.method)
        } with PsiElementClassMember[PsiMethod](sign.method, text) with ScalaNamedMember with ScalaTypedMember

class ScValueMember(member: ScValue, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          val name = element.getName
          val scType = substitutor.subst(element.getType(TypingContext.empty).getOrAny)
          val text = element.name + ": " + scType.presentableText
        } with PsiElementClassMember[ScValue](member, text) with ScalaNamedMember with ScalaTypedMember

class ScVariableMember(member: ScVariable, val element: ScTypedDefinition, val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          val name = element.getName
          val scType = substitutor.subst(element.getType(TypingContext.empty).getOrAny)
          val text = name + ": " + scType.presentableText
        } with PsiElementClassMember[ScVariable](member, text) with ScalaNamedMember with ScalaTypedMember

class JavaFieldMember(field: PsiField, val substitutor: ScSubstitutor)
        extends {
          val scType = substitutor.subst(field.getType.toScType()(field.getProject))
          val name = field.getName
          val text = name + ": " + scType.presentableText
        } with PsiElementClassMember[PsiField](field, text) with ScalaNamedMember with ScalaTypedMember