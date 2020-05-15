package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiPresentationUtils, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/
sealed trait ScalaNamedMember {
  val name: String
}

sealed trait ScalaTypedMember extends ScalaNamedMember {
  val substitutor: ScSubstitutor
  val scType: ScType
}

case class ScAliasMember(override val getElement: ScTypeAlias,
                         substitutor: ScSubstitutor,
                         isOverride: Boolean)
  extends PsiElementClassMember[ScTypeAlias](getElement, getElement.name) with ScalaNamedMember {

  override val name: String = getText
}

case class ScMethodMember(signature: PhysicalMethodSignature, isOverride: Boolean)
  extends PsiElementClassMember[PsiMethod](
    signature.method,
    ScalaPsiPresentationUtils.getMethodPresentableText(signature.method)
  ) with ScalaTypedMember {

  override val name: String = signature.name

  override val substitutor: ScSubstitutor = signature.substitutor

  override val scType: ScType = {
    val returnType = getElement match {
      case fun: ScFunction => fun.returnType.getOrAny
      case method: PsiMethod =>
        val psiType = Option(method.getReturnType).getOrElse(PsiType.VOID)
        psiType.toScType()(signature.projectContext)
    }
    substitutor(returnType)
  }
}

object ScMethodMember {

  def apply(method: PsiMethod, substitutor: ScSubstitutor = ScSubstitutor.empty, isOverride: Boolean = false): ScMethodMember =
    ScMethodMember(new PhysicalMethodSignature(method, substitutor), isOverride)
}

sealed trait ScalaFieldMember extends ScalaTypedMember

class ScValueMember(member: ScValue, val element: ScTypedDefinition, override val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          override val name = element.getName
          override val scType = substitutor(element.`type`().getOrAny)
          val text = element.name + ": " + scType.presentableText(element)
        } with PsiElementClassMember[ScValue](member, text) with ScalaFieldMember

class ScVariableMember(member: ScVariable, val element: ScTypedDefinition, override val substitutor: ScSubstitutor, val isOverride: Boolean)
        extends {
          override val name = element.getName
          override val scType = substitutor(element.`type`().getOrAny)
          val text = name + ": " + scType.presentableText(element)
        } with PsiElementClassMember[ScVariable](member, text) with ScalaFieldMember

class JavaFieldMember private(override val getElement: PsiField,
                              text: String, override val scType: ScType,
                              override val substitutor: ScSubstitutor)
  extends PsiElementClassMember[PsiField](getElement, text) with ScalaFieldMember {

  override val name: String = getElement.getName
}

object JavaFieldMember {

  def apply(field: PsiField, substitutor: ScSubstitutor): JavaFieldMember = {
    implicit val project: Project = field.getProject
    val fieldType = field.getType.toScType()
    val scType = substitutor(fieldType)

    val text = s"${field.getName}: ${scType.presentableText(field)}"
    new JavaFieldMember(field, text, scType, substitutor)
  }
}