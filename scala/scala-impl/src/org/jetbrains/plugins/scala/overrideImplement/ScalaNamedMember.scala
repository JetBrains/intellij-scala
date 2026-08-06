package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.{ClassMemberWithElement, PsiElementClassMember}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiPresentationUtils, types}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ModifiersRenderer, ParameterRenderer, ParametersRenderer, TypeAnnotationRenderer, TypeParamsRenderer, TypeRenderer}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

sealed trait ScalaMember extends ClassMemberWithElement {
  override def getElement: PsiMember
}

sealed trait ScalaNamedMember extends ScalaMember {
  val name: String
}

sealed trait ScalaTypedMember extends ScalaNamedMember {
  val substitutor: ScSubstitutor
  val scType: ScType
}

trait ScalaOverridableMember {
  def isOverride: Boolean
}

case class ScAliasMember(override val getElement: ScTypeAlias,
                         substitutor: ScSubstitutor,
                         override val isOverride: Boolean)
  extends PsiElementClassMember[ScTypeAlias](getElement, getElement.name)
    with ScalaNamedMember
    with ScalaOverridableMember {

  override val name: String = getText
}

case class ScMethodMember(
  signature: PhysicalMethodSignature,
  override val isOverride: Boolean
) extends PsiElementClassMember[PsiMethod](
  signature.method,
  ScalaPsiPresentationUtils.methodPresentableText(signature.method)
) with ScalaTypedMember
  with ScalaOverridableMember {

  override val name: String = signature.name

  override val substitutor: ScSubstitutor = signature.substitutor

  override val scType: ScType = {
    val returnType = getElement match {
      case fun: ScFunction => fun.returnType.getOrAny
      case method: PsiMethod =>
        val psiType = Option(method.getReturnType).getOrElse(PsiTypeConstants.Void)
        psiType.toScType()(signature.projectContext)
    }
    substitutor(returnType)
  }
}

object ScMethodMember {

  def apply(method: PsiMethod, substitutor: ScSubstitutor = ScSubstitutor.empty, isOverride: Boolean = false): ScMethodMember =
    ScMethodMember(new PhysicalMethodSignature(method, substitutor), isOverride)
}


/**
 * This is a special implementation of "ScalaMember".<br>
 * It groups extension methods members which belong to the same extension in the base class.
 *
 * Details:<br>
 * "Extension" member alone doesn't have a dedicated signature.<br>
 * If super class has extension with multiple extension methods: {{{
 *   extension (s: String)
 *     def extMethod1: String = ???
 *     def extMethod2: String = ???
 * }}}
 * each "extension method" will be represented by its own signature, but there will be no signature for "extension"
 *
 * We create an instance of this class only after analyzing signatures is finished to group related extension method.<br>
 * This is done to generate a nicer result during overriding/implementing extension members.<br>
 * If extension methods were grouped in the same extension in base class, we want them to be grouped in the child class.
 *
 * It's implied that it will be later used by [[org.jetbrains.plugins.scala.overrideImplement.ScalaGenerationInfo]]
 *
 * @note It's convenient to keep this class as a part of `ScalaMember` hierarchy.
 *       This is related to the fact that [[com.intellij.ide.util.MemberChooser]]
 *       and [[com.intellij.codeInsight.generation.GenerationInfoBase]]
 *       use same hierarchy of [[com.intellij.codeInsight.generation.ClassMember]]
 * @note Right now we don't expect this class is shown in "override/implement" dialog.<br>
 *       The dialog will contain each extension method separately.<br>
 *       This member will be created just before code generation
 */
//noinspection ScalaExtractStringToBundle
case class ScExtensionMember(
  extension: ScExtension,
  extensionMethodMembers: Seq[ScExtensionMethodMember]
) extends PsiElementClassMember[ScExtension](
  extension,
  "Extension" //NOTE: we don't expect this to be presented anywhere (see ScalaDoc), so using dummy placeholder
) with ScalaMember

case class ScExtensionMethodMember(
  signature: PhysicalMethodSignature,
  override val isOverride: Boolean
) extends PsiElementClassMember[PsiMethod](
  signature.method,
  ScExtensionMethodMember.extensionMethodPresentableText(signature)
) with ScalaTypedMember
  with ScalaOverridableMember {

  override val name: String = signature.name

  override val substitutor: ScSubstitutor = signature.substitutor

  override val scType: ScType = {
    val returnType = getElement match {
      case fun: ScFunction => fun.returnType.getOrAny
    }
    substitutor(returnType)
  }
}

object ScExtensionMethodMember {

  /**
   * Created by analogy with  [[org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.methodPresentableText]]<br>
   * This text is presented in the "override/implement" dialog
   */
  private def extensionMethodPresentableText(
    signature: PhysicalMethodSignature,
  ): String = {
    assert(signature.isExtensionMethod)
    val extensionSignature = signature.extensionSignature.get

    val extensionSignatureText = renderSignatureClauses(
      extensionSignature.extension.signatureClauses,
      extensionParametersRenderer
    )

    val extensionMethodText = signature.method match {
      case function: ScFunction => renderMethodPresentableText(function)
      case method               => ScalaPsiPresentationUtils.methodPresentableText(method)
    }

    s"$extensionSignatureText $extensionMethodText"
  }

  private def renderMethodPresentableText(function: ScFunction): String = {
    val buffer = new StringBuilder(function.name)
    buffer.append(renderSignatureClauses(function.signatureClauses, methodParametersRenderer))
    typeAnnotationRenderer.render(buffer, function)
    buffer.result()
  }

  private def renderSignatureClauses(
    signatureClauses: Seq[ScSignatureClause],
    parametersRenderer: ParametersRenderer
  ): String =
    signatureClauses.map {
      case ScSignatureClause.TypeClause(clause) => typeParamsRenderer.render(clause)
      case ScSignatureClause.TermClause(clause) => parametersRenderer.renderClause(clause)
    }.mkString

  private val typeRenderer: TypeRenderer = _.presentableText(TypePresentationContext.emptyContext, Context.Empty)
  private val typeAnnotationRenderer = new TypeAnnotationRenderer(typeRenderer)
  private val typeParamsRenderer = new TypeParamsRenderer(typeRenderer)
  private val extensionParametersRenderer = new ParametersRenderer(new ParameterRenderer(
    typeRenderer,
    ModifiersRenderer.SimpleText(),
    typeAnnotationRenderer
  ), shouldRenderImplicitModifier = true)
  private val methodParametersRenderer = new ParametersRenderer(new ParameterRenderer(
    typeRenderer,
    ModifiersRenderer.SimpleText(),
    typeAnnotationRenderer
  ), shouldRenderImplicitModifier = false)
}

sealed trait ScalaFieldMember extends ScalaTypedMember

sealed abstract class ScValueOrVariableMember[T <: ScValueOrVariable](
  member: T,
  val element: ScTypedDefinition,
  override val substitutor: ScSubstitutor
)(
  override val name: String = element.name,
  override val scType: ScType = substitutor(element.`type`().getOrAny)
) extends PsiElementClassMember[T](member, NlsString.force(s"$name: ${scType.presentableText(element, Context(element))}"))
  with ScalaFieldMember

class ScValueMember(
  member: ScValue,
  element: ScTypedDefinition,
  substitutor: ScSubstitutor,
  override val isOverride: Boolean
) extends ScValueOrVariableMember[ScValue](member, element, substitutor)()
  with ScalaOverridableMember

class ScVariableMember(
  member: ScVariable,
  element: ScTypedDefinition,
  substitutor: ScSubstitutor,
  override val isOverride: Boolean
) extends ScValueOrVariableMember[ScVariable](member, element, substitutor)()
  with ScalaOverridableMember

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
    implicit val tpc: TypePresentationContext = TypePresentationContext(field)
    implicit val context: types.Context = types.Context(field)

    val fieldType = field.getType.toScType()
    val scType = substitutor(fieldType)

    val text = NlsString.force(s"${field.name}: ${scType.presentableText}")
    new JavaFieldMember(field, text, scType, substitutor)
  }
}
