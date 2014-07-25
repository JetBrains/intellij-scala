package org.jetbrains.plugins.scala
package codeInspection
package sugar

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTraitParents}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}

class FunctionTupleSyntacticSugarInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaSyntacticSugar"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}

    object QualifiedName {
      def unapply(p: PsiElement): Option[String] = p match {
        case x: PsiClass => Some(x.qualifiedName)
        case _ => None
      }
    }

    import org.jetbrains.plugins.scala.codeInspection.sugar.FunctionTupleSyntacticSugarInspection._

    new ScalaElementVisitor {
      override def visitElement(elem: ScalaPsiElement) {
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
                          holder.registerProblem(holder.getManager.createProblemDescriptor(te, "syntactic sugar could be used",
                            new FunctionTypeSyntacticSugarQuickFix(te), ProblemHighlightType.WEAK_WARNING, false))
                        case Some(QualifiedName(TupleN(n))) if (te.typeArgList.typeArgs.length == n.toInt) && n.toInt != 1 =>
                          holder.registerProblem(holder.getManager.createProblemDescriptor(te, "syntactic sugar could be used",
                            new TupleTypeSyntacticSugarQuickFix(te), ProblemHighlightType.WEAK_WARNING, false))
                        case _ =>
                      }
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        super.visitElement(elem)
      }
    }
  }
}

object FunctionTupleSyntacticSugarInspection {
  val FunctionN = """scala.Function(\d)""".r
  val TupleN = """scala.Tuple(\d)""".r
  
  import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

  class TupleTypeSyntacticSugarQuickFix(te: ScParameterizedTypeElement) extends LocalQuickFix {
    def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {

      val typeTextWithParens = {
        val needParens = te.getContext match {
          case ft: ScFunctionalTypeElement => true // (Tuple2[A, B]) => B  ==>> ((A, B)) => C
          case _ => false
        }
        ("(" + te.typeArgList.getText.drop(1).dropRight(1) + ")").parenthesisedIf(needParens)
      }
      te.replace(createTypeElementFromText(typeTextWithParens, te.getManager))
    }

    def getName: String = "Replace TupleN[A1, A1, ...,  AN] with (A1, A1, ...,  AN)"

    def getFamilyName: String = getName
  }

  class FunctionTypeSyntacticSugarQuickFix(te: ScParameterizedTypeElement) extends LocalQuickFix {
    def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
      val paramTypes = te.typeArgList.typeArgs.dropRight(1)
      val returnType = te.typeArgList.typeArgs.last
      val elemsInParamTypes = if (paramTypes.isEmpty) Seq.empty else ScalaPsiUtil.getElementsRange(paramTypes.head, paramTypes.last)

      val returnTypeTextWithParens = {
        val returnTypeNeedParens = returnType match {
          case ft: ScFunctionalTypeElement => true
          case ft: ScInfixTypeElement => true
          case _ => false
        }
        returnType.getText.parenthesisedIf(returnTypeNeedParens)
      }
      val typeTextWithParens = {
        val needParens = te.getContext match {
          case ft: ScFunctionalTypeElement => true
          case ft: ScInfixTypeElement => true
          case _: ScConstructor | _: ScTraitParents | _: ScClassParents => true
          case _ => false
        }
        val arrow = ScalaPsiUtil.functionArrow(project)
        s"(${elemsInParamTypes.map(_.getText).mkString}) $arrow $returnTypeTextWithParens".parenthesisedIf(needParens)
      }
      te.replace(createTypeElementFromText(typeTextWithParens, te.getManager))
    }

    def getName: String = "Replace FunctionN[A1, A1, ...,  AN, R] with (A1, A1, ...,  AN) => R"

    def getFamilyName: String = getName
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