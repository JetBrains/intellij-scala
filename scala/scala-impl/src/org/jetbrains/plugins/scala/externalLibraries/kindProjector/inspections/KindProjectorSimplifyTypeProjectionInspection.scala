package org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.{KindProjectorUtil, TypeLambda}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScMatchType, ScParameterizedType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.collection.mutable
/**
 * Simplifies types, so that they use Kind Projector plugin (if Kind Projector is enabled)
 * @see https://github.com/non/kind-projector
 */
class KindProjectorSimplifyTypeProjectionInspection extends LocalInspectionTool {
  import KindProjectorSimplifyTypeProjectionInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    holder.getFile match {
      case _: ScalaFile =>
        new ScalaElementVisitor {
          override def visitTypeProjection(projection: ScTypeProjection): Unit = projection match {
            case TypeLambda(alias) =>
              if (alias.kindProjectorEnabled) {
                val aliasParam = alias.typeParameters
                projection.parent match {
                  //should be handled by AppliedTypeLambdaCanBeSimplifiedInspection
                  case Some(p: ScParameterizedTypeElement) if p.typeArgList.typeArguments.size == aliasParam.size => ()
                  case _ if aliasParam.nonEmpty && aliasParam.forall(canConvertBounds) =>
                    val fix = new KindProjectorSimplifyTypeProjectionQuickFix(projection, convertToKindProjectorSyntax(alias))
                    holder.registerProblem(projection, inspectionName, fix)
                  case _ => ()
                }
              }
            case _ => ()
          }
        }
      case _ => PsiElementVisitor.EMPTY_VISITOR
    }

  override def getDisplayName: String = inspectionName
  override def getID: String          = inspectionId
}

object KindProjectorSimplifyTypeProjectionInspection {
  private val inspectionId: String   = "KindProjectorSimplifyTypeProjection"
  private val inspectionName: String = ScalaInspectionBundle.message("displayname.in.kind.projector.simplify.type")

  private val TupleN = "_root_.scala.Tuple(\\d+)".r
  private val FunctionN = "_root_.scala.Function(\\d+)".r

  final class KindProjectorSimplifyTypeProjectionQuickFix(e: PsiElement, replacement: => String)
    extends AbstractFixOnPsiElement(KindProjectorSimplifyTypeProjectionInspection.inspectionName, e) {

    override protected def doApplyFix(elem: PsiElement)(implicit project: Project): Unit =
      elem.replace(createTypeElementFromText(replacement, e, null))

    override def getFileModifierForPreview(target: PsiFile): FileModifier =
      new KindProjectorSimplifyTypeProjectionQuickFix(PsiTreeUtil.findSameElementInCopy(e, target), replacement)
  }


  private[this] def boundsDefined(param: ScTypeParam): Boolean =
    param.lowerTypeElement.isDefined || param.upperTypeElement.isDefined

  /**
    * Kind projector currently supports only very basic type bounds
    * @see https://github.com/non/kind-projector/pull/6
    */
  private[kindProjector] def canConvertBounds(param: ScTypeParam): Boolean =
    hasNoBounds(param) || ((param.lowerTypeElement, param.upperTypeElement) match {
      case (Some(_: ScSimpleTypeElement) | None, Some(_: ScSimpleTypeElement) | None) => true
      case _                                                                          => false
    })

  private[this] def hasNoBounds(p: ScTypeParam): Boolean = {
    (p.lowerTypeElement, p.upperTypeElement) match {
      case (None, None) => true
      case _            => false
    }
  }

  private[this] def tryConvertToInlineSyntax(alias: ScTypeAliasDefinition): Option[String] = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(alias)
    implicit val context: Context = Context(alias)

    def simpleTypeArgumentOccurrences(tpe: ScParameterizedType): Map[String, Int] =
      tpe.typeArguments.collect { case tpt: TypeParameterType => tpt.name }
        .groupBy(identity)
        .view
        .mapValues(_.size)
        .toMap

    alias.aliasedType match {
      case Right(paramType: ScParameterizedType) =>
        val typeParams            = alias.typeParameters
        val validTypeParams       = typeParams.nonEmpty && typeParams.forall(hasNoBounds)
        val typeArgOccurrences    = simpleTypeArgumentOccurrences(paramType)
        val typeParamsAppearOnce  = typeParams.map(p => typeArgOccurrences.getOrElse(p.name, 0)).forall(_ == 1)

        if (validTypeParams && typeParamsAppearOnce) {
          val typeParamIt      = typeParams.iterator
          var currentTypeParam = Option(typeParamIt.next())

          val newTypeArgs = paramType.typeArguments.map { ta =>
            currentTypeParam match {
              case Some(tpt) if ta.presentableText == tpt.name =>
                currentTypeParam =
                  if (typeParamIt.hasNext) Option(typeParamIt.next())
                  else                     None
                tpt.getText.replace(tpt.name, KindProjectorUtil.placeholderSymbolFor(alias))
              case _ =>
                if (ScalaApplicationSettings.PRECISE_TEXT) ta.canonicalText
                else ta.presentableText
            }
          }

          paramType.designator.canonicalText match {
            case TupleN(n) if n != "1" =>
              Some(newTypeArgs.mkString("(", ", ", ")"))
            case FunctionN(n) if n != "0" =>
              val needsParentheses = n != "1" || paramType.typeArguments.headOption.exists {
                case FunctionType(_, _) => true
                case TupleType(_) => true
                case _: ScMatchType => true
                case _ => false
              }
              val args = if (needsParentheses) newTypeArgs.init.mkString("(", ", ", ")") else newTypeArgs.head
              Some(s"$args ${ScalaPsiUtil.functionArrow(alias)} ${newTypeArgs.last}")
            case _ =>
              (!typeParamIt.hasNext && currentTypeParam.isEmpty).option {
                val designatorText =
                  if (ScalaApplicationSettings.PRECISE_TEXT) paramType.designator.canonicalText
                  else paramType.designator.presentableText
                s"$designatorText${newTypeArgs.mkString(start = "[", sep = ", ", end = "]")}"
              }
          }
        } else None
      case _ => None
    }
  }

  private[this] def convertToFunctionSyntax(alias: ScTypeAliasDefinition): String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(alias)
    implicit val context: Context = Context(alias)

    val builder = new mutable.StringBuilder()

    // TODO Use standard type lambda presentation (as without -Ykind-projector), SCL-23275
    if (alias.isInScala3File) {
      builder ++= alias.typeParameters.map(_.getText).mkString("[", ", ", "]")
      builder ++= " =>> "
      builder ++= {
        val tpe = alias.aliasedType.getOrAny
        if (ScalaApplicationSettings.PRECISE_TEXT) tpe.canonicalText
        else tpe.presentableText
      }
    } else {
      val styleSettings = ScalaCodeStyleSettings.getInstance(alias.getProject)

      if (styleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER) builder ++= "λ"
      else builder ++= "Lambda"

      builder.append("[")
      val parameters = alias.typeParameters.map(param =>
        if (param.isCovariant || param.isContravariant || boundsDefined(param))
          s"`${param.getText}`"
        else param.getText
      )

      if (parameters.length > 1) builder ++= parameters.mkString(start = "(", sep = ", ", end = ")")
      else builder ++= parameters.mkString(start = "", sep = "", end = "")

      builder ++= " => "
      builder ++= alias.aliasedType.getOrAny.presentableText
      builder ++= "]"
    }

    builder.toString()
  }

  // TODO use TypePresentation's innerTypeText & NameRenderer, SCL-23282
  def convertToKindProjectorSyntax(alias: ScTypeAliasDefinition): String =
    tryConvertToInlineSyntax(alias).getOrElse(convertToFunctionSyntax(alias))
}
