package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.deprecation.ScalaDeprecationInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScAnnotationsHolder, ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaDeprecationInspection extends LocalInspectionTool {

  override def getID: String = "ScalaDeprecation"

  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    def checkDeprecated(result: ScalaResolveResult, elementToHighlight: PsiElement, name: String): Unit =
      result.element match {
        case param: ScParameter if result.isNamedParameter && !ScalaNamesUtil.equivalent(param.name, name) =>
          param.deprecatedName.foreach { deprecatedName =>
            holder.registerProblem(elementToHighlight, ScalaInspectionBundle.message("parameter.name.is.deprecated", deprecatedName))
          }
        case named: PsiNamedElement =>
          val context = named.nameContext

          val deprecatedElement = context.asOptionOf[PsiDocCommentOwner].flatMap {
            case Constructor(constr) if constr.isDeprecated => Some(constr)
            case Constructor.ofClass(clazz) if clazz.isDeprecated => Some(clazz)
            case func@ScFunction.inSynthetic(clazz) if func.isApplyMethod && clazz.isDeprecated => Some(clazz)
            case other if other.isDeprecated => Some(other)
            case obj: ScObject =>
              obj.fakeCompanionClassOrCompanionClass match {
                case enum: ScEnum if enum.isDeprecated => Option(obj)
                case ScEnum.Original(enum) if enum.isDeprecated => Option(obj)
                case _ => None
              }
            case _: ScFunction =>
              result.getActualElement.asOptionOfUnsafe[PsiDocCommentOwner].filter(_.isDeprecated)
            case _ =>
              None
          }

          deprecatedElement.foreach { deprecatedElement =>
            val message = deprecationMessage(deprecatedElement).getOrElse("")
            holder.registerProblem(elementToHighlight, ScalaInspectionBundle.message("symbol.name.is.deprecated.with.message", name, message))
          }
        case _ => ()
      }

    def checkDeprecatedInheritance(result: ScalaResolveResult, elementToHighlight: PsiElement, name: String): Unit = {
      result.getActualElement match {
        case owner: PsiAnnotationOwner if owner.hasAnnotation("scala.deprecatedInheritance") =>
          val message = deprecationMessage(owner).getOrElse("")
          holder.registerProblem(elementToHighlight, ScalaInspectionBundle.message("inheriting.form.name.is.deprecated.message", name, message))
        case _ =>
      }
    }

    def checkOverridingDeprecated(superMethod: PsiMethod, method: ScFunction): Unit = {
      superMethod match {
        case owner if owner.isDeprecated =>
          val message = deprecationMessage(owner).getOrElse("")
          holder.registerProblem(method.nameId, ScalaInspectionBundle.message("super.method.name.is.deprecated.with.message", method.name, message))
        case owner  if owner.hasAnnotation("scala.deprecatedOverriding") =>
          val message = deprecationMessage(owner).getOrElse("")
          holder.registerProblem(method.nameId, ScalaInspectionBundle.message("overriding.is.deprecated", method.name, message))
        case _ =>
      }
    }

    new ScalaElementVisitor {

      override def visitFunction(fun: ScFunction): Unit =
        if (fun.isDefinedInClass) {
          fun.superMethods.foreach(checkOverridingDeprecated(_, fun))
        }

      override def visitReference(ref: ScReference): Unit =
        if (ref.isValid) {
          val resolveResult = ref.bind()
          resolveResult.foreach { rr =>
            ref match {
                // find inheriting references like
                //   class Test extends Base
                //   new Test {  }
                // but not
                //   new Test
              case Parent(Parent((_: ScConstructorInvocation) & Parent(Parent(exb: ScExtendsBlock))))
                if !exb.getParent.is[ScNewTemplateDefinition] || exb.isAnonymousClass =>
                checkDeprecatedInheritance(rr, ref.nameId, ref.refName)
              case _ =>
            }
            checkDeprecated(rr, ref.nameId, ref.refName)
          }
        }

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = visitReference(ref)
      override def visitTypeProjection(proj: ScTypeProjection): Unit = visitReference(proj)
    }
  }
}

object ScalaDeprecationInspection {

  private val ScalaDeprecatedAnnotation = "scala.deprecated"
  private val ScalaDeprecatedAnnotationMessageField = "value"

  private def deprecationMessage(commentOwner: PsiElement): Option[String] =
    for {
      holder     <- commentOwner.asOptionOfUnsafe[ScAnnotationsHolder]
      annotation <- holder.annotations(ScalaDeprecatedAnnotation).headOption
      message    <- ScalaPsiUtil.readAttribute(annotation, ScalaDeprecatedAnnotationMessageField)
    } yield message
}
