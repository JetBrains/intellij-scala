package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

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
    ScalaPsiPresentationUtils.methodPresentableText(signature.method)
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

sealed abstract class ScValueOrVariableMember[T <: ScValueOrVariable](member: T,
                                                                      val element: ScTypedDefinition,
                                                                      override val substitutor: ScSubstitutor)(
                                                                      override val name: String = element.name,
                                                                      override val scType: ScType = substitutor(element.`type`().getOrAny))
  extends PsiElementClassMember[T](member, NlsString.force(s"$name: ${scType.presentableText(element)}")) with ScalaFieldMember

class ScValueMember(member: ScValue, element: ScTypedDefinition, substitutor: ScSubstitutor, val isOverride: Boolean)
        extends ScValueOrVariableMember[ScValue](member, element, substitutor)()

class ScVariableMember(member: ScVariable, element: ScTypedDefinition, substitutor: ScSubstitutor, val isOverride: Boolean)
      extends ScValueOrVariableMember[ScVariable](member, element, substitutor)()

class JavaFieldMember private(override val getElement: PsiField,
                              @Nls text: String,
                              override val scType: ScType,
                              override val substitutor: ScSubstitutor)
  extends PsiElementClassMember[PsiField](getElement, text) with ScalaFieldMember {

  override val name: String = getElement.getName
}

object JavaFieldMember {

  def apply(field: PsiField, substitutor: ScSubstitutor): JavaFieldMember = {
    implicit val project: Project = field.getProject
    val fieldType = field.getType.toScType()
    val scType = substitutor(fieldType)

    val text = NlsString.force(s"${field.name}: ${scType.presentableText(field)}")
    new JavaFieldMember(field, text, scType, substitutor)
  }
}