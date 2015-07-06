package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorSimplifyTypeProjectionInspection.{inspectionId, inspectionName}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

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

    holder.getFile match {
      case _: ScalaFile =>
        new ScalaElementVisitor {
          override def visitTypeProjection(projection: ScTypeProjection): Unit = {
            if (ScalaPsiUtil.kindProjectorPluginEnabled(projection)) {
              projection.typeElement match {
                case parenType: ScParenthesisedTypeElement => parenType.typeElement match {
                  case Some(ct: ScCompoundTypeElement) =>
                    ct.refinement match {
                      case Some(refinement) =>
                        refinement.types match {
                          case Seq(alias: ScTypeAliasDefinition) if alias.nameId.getText == projection.nameId.getText =>
                            val aliasParam = alias.typeParameters
                            projection.parent match {
                              case Some(p: ScParameterizedTypeElement) if p.typeArgList.typeArgs.size == aliasParam.size =>
                              //should be handled by AppliedTypeLambdaCanBeSimplifiedInspection
                              case _ =>
                                if (alias.typeParameters.forall(canConvertBounds)) {
                                  lazy val simplified: String = {
                                    //currently we do not try to convert to inline syntax.
                                    //It gets too complex with nested parameterized types
                                    val builder = new StringBuilder
                                    builder.append("λ[")
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
                                  val fix = new KindProjectorSimplifyTypeProjectionQuickFix(projection, simplified)
                                  holder.registerProblem(projection, inspectionName, fix)
                                }
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
      case _ => new PsiElementVisitor {}
    }
  }

  override def getDisplayName: String = inspectionName

  override def getID: String = inspectionId
}

class KindProjectorSimplifyTypeProjectionQuickFix(e: PsiElement, replacement: => String) extends
AbstractFixOnPsiElement(inspectionName, e) {
  override def doApplyFix(project: Project): Unit = {
    val elem = getElement
    if (!elem.isValid) return

    val te = ScalaPsiElementFactory.createTypeElementFromText(replacement, elem.getManager)
    elem.replace(te)
  }
}

object KindProjectorSimplifyTypeProjectionInspection {
  val inspectionId = "KindProjectorSimplifyTypeProjection"
  val inspectionName = InspectionBundle.message("kind.projector.simplify.type")
}
