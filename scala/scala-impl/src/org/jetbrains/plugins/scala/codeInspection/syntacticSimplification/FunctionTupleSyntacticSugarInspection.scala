package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType}

import scala.util.matching.Regex

class FunctionTupleSyntacticSugarInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaSyntacticSugar"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.is[ScalaFile]) return PsiElementVisitor.EMPTY_VISITOR

    object QualifiedName {
      def unapply(p: PsiElement): Option[String] = p match {
        case x: PsiClass => Some(x.qualifiedName)
        case _ => None
      }
    }

    import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.FunctionTupleSyntacticSugarInspection._

    new ScalaElementVisitor {
      override def visitScalaElement(elem: ScalaPsiElement): Unit = {
        elem match {
          case te: ScParameterizedTypeElement =>
            te.typeElement match {
              case s: ScSimpleTypeElement =>
                s.reference match {
                  case Some(ref) =>
                    if (ref.refName.startsWith("Tuple") || ref.refName.startsWith("Function") && ref.isValid) {
                      val referredElement = ref.bind().map(_.getElement)
                      referredElement match {
                        case Some(QualifiedName(FunctionN(n))) if te.typeArgList.typeArgs.length == (n.toInt + 1) =>
                          holder.registerProblem(holder.getManager.createProblemDescriptor(te, ScalaInspectionBundle.message("syntactic.sugar.could.be.used"),
                            new FunctionTypeSyntacticSugarQuickFix(te), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false))
                        case Some(QualifiedName(TupleN(n))) if (te.typeArgList.typeArgs.length == n.toInt) && n.toInt != 1 =>
                          holder.registerProblem(holder.getManager.createProblemDescriptor(te, ScalaInspectionBundle.message("syntactic.sugar.could.be.used"),
                            new TupleTypeSyntacticSugarQuickFix(te), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false))
                        case _ =>
                      }
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        super.visitScalaElement(elem)
      }
    }
  }
}

object FunctionTupleSyntacticSugarInspection {
  val FunctionN: Regex = raw"${FunctionType.TypeName}(\d)".r
  val TupleN: Regex = raw"${TupleType.TypeName}(\d)".r

  import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

  class TupleTypeSyntacticSugarQuickFix(te: ScParameterizedTypeElement)
          extends AbstractFixOnPsiElement(ScalaBundle.message("replace.tuple.type"), te) {

    override protected def doApplyFix(typeElement: ScParameterizedTypeElement)
                                     (implicit project: Project): Unit = {
      val typeTextWithParens = {
        val needParens = typeElement.getContext match {
          case _: ScFunctionalTypeElement => true // (Tuple2[A, B]) => B  ==>> ((A, B)) => C
          case _ => false
        }
        ("(" + typeElement.typeArgList.getText.drop(1).dropRight(1) + ")").parenthesize(needParens)
      }
      typeElement.replace(createTypeElementFromText(typeTextWithParens, typeElement))
    }
  }

  class FunctionTypeSyntacticSugarQuickFix(te: ScParameterizedTypeElement)
          extends AbstractFixOnPsiElement(ScalaBundle.message("replace.fun.type"), te) {

    override protected def doApplyFix(typeElement: ScParameterizedTypeElement)
                                     (implicit project: Project): Unit = {
      val paramTypes = typeElement.typeArgList.typeArgs.dropRight(1)
      val returnType = typeElement.typeArgList.typeArgs.last
      val elemsInParamTypes = if (paramTypes.isEmpty) Seq.empty else ScalaPsiUtil.getElementsRange(paramTypes.head, paramTypes.last)

      val returnTypeTextWithParens = {
        val returnTypeNeedParens = returnType match {
          case _: ScFunctionalTypeElement => true
          case _: ScInfixTypeElement => true
          case _ => false
        }
        returnType.getText.parenthesize(returnTypeNeedParens)
      }
      val typeTextWithParens = {
        val needParens = typeElement.getContext match {
          case _: ScFunctionalTypeElement => true
          case _: ScInfixTypeElement => true
          case _: ScConstructorInvocation | _: ScTemplateParents => true
          case _ => false
        }
        val arrow = ScalaPsiUtil.functionArrow(project)
        s"(${elemsInParamTypes.map(_.getText).mkString}) $arrow $returnTypeTextWithParens".parenthesize(needParens)
      }
      typeElement.replace(createTypeElementFromText(typeTextWithParens, typeElement))
    }
  }
}

// TODO: Test
/*
object sugar {
  type a1 = Function2[Int, /*comment*/ String, Int]
  type a2 = Tuple3[Int, /*comment*/ String, Int]
  type a3 = Function1[Int, Int] => Int
  type a4 = Tuple2[Int, Int] => Int
  type a5 = Tuple2[Int, Int] <:< Function1[Int, Int]
  type a6 = Function1[Int, () => Int] <:< Function1[Int, Int]
  type a7 = Function0[Int] <:< Function1[Int, Int]
}

object sugar {
  type a1 = (Int, /*comment*/ String) => Int
  type a2 = (Int, /*comment*/ String, Int)
  type a3 = ((Int) => Int) => Int
  type a4 = ((Int, Int)) => Int
  type a5 = (Int, Int) <:< ((Int) => Int)
  type a6 = ((Int) => (() => Int)) <:< ((Int) => Int)
  type a7 = (() => Int) <:< ((Int) => Int)
}
*/
