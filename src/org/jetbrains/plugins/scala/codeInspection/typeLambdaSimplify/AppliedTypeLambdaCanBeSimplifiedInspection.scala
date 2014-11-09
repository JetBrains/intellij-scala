package org.jetbrains.plugins.scala
package codeInspection
package typeLambdaSimplify

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}

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
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaAppliedTypeLambdaCanBeSimplified"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}

    def addInfo(paramType: ScParameterizedTypeElement, replacementText: => String) = {
      val fixes = Array[LocalQuickFix](new SimplifyAppliedTypeLambdaQuickFix(paramType, replacementText))
      val problem = holder.getManager.createProblemDescriptor(paramType, getShortName, isOnTheFly,
        fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      holder.registerProblem(problem)
    }

    new ScalaElementVisitor {
      override def visitElement(elem: ScalaPsiElement): Unit = elem match {
        case paramType: ScParameterizedTypeElement => paramType.typeElement match {
          case typeProjection: ScTypeProjection => typeProjection.typeElement match {
            case parenType: ScParenthesisedTypeElement => parenType.typeElement match {
              case Some(ct: ScCompoundTypeElement) =>
                (ct.components, ct.refinement) match {
                  case (Seq(), Some(refinement)) =>
                    (refinement.holders, refinement.types) match {
                      case (Seq(), Seq(typeAliasDefinition: ScTypeAliasDefinition)) =>
                        val name1 = typeProjection.nameId
                        val name2 = typeAliasDefinition.nameId
                        if (name1.getText == name2.getText) {
                          val at: TypeResult[ScType] = typeAliasDefinition.aliasedType
                          println(at, paramType.typeArgList)
                          val params = typeAliasDefinition.typeParameters
                          val typeArgs = paramType.typeArgList.typeArgs
                          if (params.length == typeArgs.length) {
                            lazy val simplified: String = {
                              val aliased = typeAliasDefinition.aliasedType.getOrAny
                              val subst = params.zip(typeArgs).foldLeft(ScSubstitutor.empty) {
                                case (res, (param, arg)) =>
                                  val typeVar = ScalaPsiManager.typeVariable(param)
                                  res.bindT((typeVar.name, typeVar.getId), arg.calcType)
                              }
                              val substituted = subst.subst(aliased)
                              ScType.presentableText(substituted)
                            }
                            addInfo(paramType, simplified)
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
          case _ =>
        }
        case _ =>
      }
    }
  }
}

class SimplifyAppliedTypeLambdaQuickFix(paramType: ScParameterizedTypeElement, replacement: => String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    val parent = paramType.getContext
    paramType.replace(ScalaPsiElementFactory.createTypeElementFromText(replacement, paramType.getManager))
  }

  def getName: String = "Simplify type"

  def getFamilyName: String = getName
}

