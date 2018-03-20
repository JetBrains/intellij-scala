package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorSimplifyTypeProjectionInspection.{inspectionId, inspectionName}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/3/15
 *
 * Simplifies types, so that they use Kind Projector plugin (if Kind Projector is enabled)
 * @see https://github.com/non/kind-projector
 */
class KindProjectorSimplifyTypeProjectionInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    def boundsDefined(param: ScTypeParam) = {
      param.lowerTypeElement.isDefined || param.upperTypeElement.isDefined
    }

    /**
     * Kind projector currently supports only very basic type bounds
     * @see https://github.com/non/kind-projector/pull/6
     */
    def canConvertBounds(param: ScTypeParam): Boolean = {
      param.lowerTypeElement match {
        case Some(_: ScSimpleTypeElement) | None =>
          param.upperTypeElement match {
            case Some(_: ScSimpleTypeElement) | None => true
            case _ => false
          }
        case _ => false
      }
    }

    def tryConvertToInlineSyntax(alias: ScTypeAliasDefinition): Option[String] = {
      def hasNoBounds(p: ScTypeParam): Boolean = {
        (p.lowerTypeElement, p.upperTypeElement) match {
          case (None, None) => true
          case _ => false
        }
      }

      def occursInsideParameterized(tp: ScTypeParam, param: ScParameterizedType, isInsideParam: Boolean): Boolean = {
        param.typeArguments.exists {
          case p: ScParameterizedType if isInsideParam && p.designator.presentableText == tp.name => true
          case p: ScParameterizedType if occursInsideParameterized(tp, p, isInsideParam = true) => true
          case ta if isInsideParam && ta.presentableText == tp.name => true
          case _ => false
        }
      }

      alias.aliasedType match {
        case Right(paramType: ScParameterizedType) =>
          val typeParam: Seq[ScTypeParam] = alias.typeParameters
          val valid =
            typeParam.nonEmpty &&
            typeParam.forall(hasNoBounds) &&
            !typeParam.exists(occursInsideParameterized(_, paramType, isInsideParam = false)) &&
            typeParam.forall {
              tpt =>
                paramType.typeArguments.count(tpt.name == _.presentableText) == 1
            }

          if (valid) {
            val typeParamIt = typeParam.iterator
            var currentTypeParam: Option[ScTypeParam] = Some(typeParamIt.next())
            val newTypeArgs = paramType.typeArguments.map { ta =>
              currentTypeParam match {
                case Some(tpt) if ta.presentableText == tpt.name =>
                  currentTypeParam =
                    if (typeParamIt.hasNext) Some(typeParamIt.next())
                    else None
                  tpt.getText.replace(tpt.name, "?")
                case _ => ta.presentableText
              }
            }
            if (!typeParamIt.hasNext && currentTypeParam.isEmpty) {
              Some(s"${paramType.designator}${newTypeArgs.mkString(start = "[", sep = ",", end = "]")}")
            } else None
          } else None
        case _ => None
      }
    }

    holder.getFile match {
      case _: ScalaFile =>
        new ScalaElementVisitor {
          override def visitTypeProjection(projection: ScTypeProjection): Unit = {
            if (ScalaPsiUtil.kindProjectorPluginEnabled(projection)) {
              projection.typeElement match {
                case parenType: ScParenthesisedTypeElement => parenType.innerElement match {
                  case Some(ct: ScCompoundTypeElement) =>
                    ct.refinement match {
                      case Some(refinement) =>
                        refinement.types match {
                          case Seq(alias: ScTypeAliasDefinition) if alias.nameId.getText == projection.nameId.getText =>
                            val aliasParam = alias.typeParameters
                            projection.parent match {
                              case Some(p: ScParameterizedTypeElement) if p.typeArgList.typeArgs.size == aliasParam.size =>
                              //should be handled by AppliedTypeLambdaCanBeSimplifiedInspection
                              case _ if aliasParam.nonEmpty =>
                                if (alias.typeParameters.forall(canConvertBounds)) {
                                  def simplified(): String = {
                                    tryConvertToInlineSyntax(alias) match {
                                      case Some(inline) => inline
                                      case _ => //convert to function syntax
                                        val builder = new StringBuilder
                                        val styleSettings = ScalaCodeStyleSettings.getInstance(projection.getProject)
                                        if (styleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER) {
                                          builder.append("λ")
                                        } else {
                                          builder.append("Lambda")
                                        }
                                        builder.append("[")
                                        val parameters = aliasParam.map { param: ScTypeParam =>
                                          if (param.isCovariant || param.isContravariant || boundsDefined(param)) {
                                            s"`${param.getText}`"
                                          } else param.getText
                                        }
                                        if (parameters.length > 1) {
                                          builder.append(parameters.mkString(start = "(", sep = ",", end = ")"))
                                        } else builder.append(parameters.mkString(start = "", sep = "", end = ""))
                                        builder.append(" => ")
                                        builder.append(alias.aliasedType.getOrAny)
                                        builder.append("]")
                                        builder.toString()
                                    }
                                  }
                                  val fix = new KindProjectorSimplifyTypeProjectionQuickFix(projection, simplified())
                                  holder.registerProblem(projection, inspectionName, fix)
                                }
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
                case _ =>
              }
            }
          }
        }
      case _ => PsiElementVisitor.EMPTY_VISITOR
    }
  }

  override def getDisplayName: String = inspectionName

  override def getID: String = inspectionId
}

class KindProjectorSimplifyTypeProjectionQuickFix(e: PsiElement, replacement: => String) extends
AbstractFixOnPsiElement(inspectionName, e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    elem.replace(createTypeElementFromText(replacement))
  }
}

object KindProjectorSimplifyTypeProjectionInspection {
  val inspectionId = "KindProjectorSimplifyTypeProjection"
  val inspectionName = InspectionBundle.message("kind.projector.simplify.type")
}
