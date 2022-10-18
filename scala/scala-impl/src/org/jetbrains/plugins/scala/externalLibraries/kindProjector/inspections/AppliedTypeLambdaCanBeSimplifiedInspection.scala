package org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.TypeLambda
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * Inspection to simplify a type like:
 *
 * ({type l[a] = Either[String, a]})#l[Int]
 *
 * to
 *
 * Either[String, Int]
 *
 * Such a type can appear after overide/implement when using Type Lamdas.
 *
 * For example:
 * {{{
 * trait Parent[M[_]] { def abstracto(m: M[Int]) }
 *
 * trait Child1 extends Parent[({type l[a]=Either[String,a]})#l] {
 *    // implement methods
 * }
 * }}}
 */
class AppliedTypeLambdaCanBeSimplifiedInspection extends LocalInspectionTool {
  import AppliedTypeLambdaCanBeSimplifiedInspection._

  override def isEnabledByDefault: Boolean = true
  override def getID: String               = inspectionId
  override def getDisplayName: String      = inspectionName

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    def inspectTypeProjection(
      alias:         ScTypeAliasDefinition,
      parameterized: ScParameterizedTypeElement
    ): Unit = {
      val typeArgs = parameterized.typeArgList.typeArgs

      if (alias.typeParameters.size == typeArgs.size) {
        val fix = new SimplifyAppliedTypeLambdaQuickFix(parameterized, simplifyTypeProjection(alias, typeArgs)(parameterized))
        val problem = holder.getManager.createProblemDescriptor(
          parameterized,
          getDisplayName,
          isOnTheFly,
          Array[LocalQuickFix](fix),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )

        holder.registerProblem(problem)
      }
    }

    holder.getFile match {
      case _: ScalaFile =>
        new ScalaElementVisitor {
          override def visitParameterizedTypeElement(elem: ScParameterizedTypeElement): Unit = elem.typeElement match {
            case TypeLambda(alias) => inspectTypeProjection(alias, elem)
            case typeLambda: ScParameterizedTypeElement if elem.kindProjectorEnabled =>
              /* def a: λ[A => (A, A)][String]
                 can be transformed into
                 def a: (String, String) */
              typeLambda.computeDesugarizedType match {
                case Some(TypeLambda(alias)) => inspectTypeProjection(alias, elem)
                case _                       => ()
              }
            case _ => ()
          }
        }
      case _ => PsiElementVisitor.EMPTY_VISITOR
    }
  }
}

object AppliedTypeLambdaCanBeSimplifiedInspection {
  private val inspectionId: String = "ScalaAppliedTypeLambdaCanBeSimplified"
  private val inspectionName: String = ScalaInspectionBundle.message("applied.type.lambda.can.be.simplified")

  def simplifyTypeProjection(alias: ScTypeAliasDefinition, typeArgs: Seq[ScTypeElement])(implicit tpc: TypePresentationContext): String = {
    val aliased     = alias.aliasedType.getOrAny
    val subst       = ScSubstitutor.bind(alias.typeParameters, typeArgs)(_.calcType)
    val substituted = subst(aliased)
    substituted.presentableText
  }

  final class SimplifyAppliedTypeLambdaQuickFix(paramType: ScParameterizedTypeElement, @SafeFieldForPreview replacement: => String)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("simplify.type"), paramType) {

    override protected def doApplyFix(element: ScParameterizedTypeElement)(implicit project: Project): Unit =
      element.replace(createTypeElementFromText(replacement, element))
  }
}
