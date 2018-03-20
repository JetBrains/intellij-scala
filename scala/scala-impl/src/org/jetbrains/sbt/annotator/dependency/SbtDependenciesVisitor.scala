package org.jetbrains.sbt.annotator.dependency

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.sbt.annotator.dependency.AddSbtDependencyUtils._

/**
  * Created by afonichkin on 8/28/17.
  */
private object SbtDependenciesVisitor {
  @scala.annotation.tailrec
  private def getScPatternDefinition(psiElement: PsiElement): ScPatternDefinition = {
    psiElement match {
      case pattern: ScPatternDefinition => pattern
      case _: PsiFile => null
      case _ => getScPatternDefinition(psiElement.getParent)
    }
  }

  def processMethodCall(call: ScMethodCall)(f: PsiElement => Unit): Unit = {
    f(call)

    def processSettings(settings: ScMethodCall): Unit = {
      settings.args.exprsArray.foreach({
        case typedStmt: ScTypedStmt => processTypedStmt(typedStmt)(f)
        case infix: ScInfixExpr if infix.left.getText == LIBRARY_DEPENDENCIES => processInfix(infix)(f)
        case call: ScMethodCall => processMethodCall(call)(f)
        case ref: ScReferenceExpression => processReferenceExpr(ref)(f)
        case _ =>
      })
    }

    if (call.deepestInvokedExpr.getText == SEQ) {
      for {
        callType <- call.`type`().toOption
        formalSeq <- ScalaPsiElementFactory.createTypeFromText(SBT_SEQ_TYPE, call, call)
        formalSetting <- ScalaPsiElementFactory.createTypeFromText(SBT_SETTING_TYPE, call, call)
      } yield callType match {
        case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSeq) =>
          val args = parameterized.typeArguments
          if (args.length == 1) {
            args.head match {
              case parameterizedArg: ScParameterizedType if parameterizedArg.designator.equiv(formalSetting) =>
                processSettings(call)
              case _ =>
            }
          }
      }
    } else
      call.getEffectiveInvokedExpr match {
        case expr: ScReferenceExpression if expr.refName == SETTINGS =>
          processSettings(call)
        case _ =>
      }
  }

  def processPatternDefinition(patternDefinition: ScPatternDefinition)(f: PsiElement => Unit): Unit = {
    f(patternDefinition)

    val maybeTypeName = patternDefinition.`type`().toOption
      .map(_.canonicalText)

    if (maybeTypeName.contains(SBT_PROJECT_TYPE)) {
      getSettings(patternDefinition).foreach(processMethodCall(_)(f))
    } else {
      patternDefinition.expr match {
        case Some(call: ScMethodCall) => processMethodCall(call)(f)
        case Some(infix: ScInfixExpr) => processInfix(infix)(f)
        case _ =>
      }
    }
  }

  def processInfix(infix: ScInfixExpr)(f: PsiElement => Unit): Unit = {
    f(infix)

    def process(expr: ScExpression): Unit = {
      expr match {
        case call: ScMethodCall => processMethodCall(call)(f)
        case infix: ScInfixExpr => processInfix(infix)(f)
        case ref: ScReferenceExpression => processReferenceExpr(ref)(f)
        case _ =>
      }
    }

    if (infix.operation.refName == "++") {
      process(infix.left)
      process(infix.right)
    } else if (infix.operation.refName == "++=") {
      process(infix.right)
    } else if (infix.operation.refName == ":=") {
      process(infix.right)
    }
  }

  def processReferenceExpr(ref: ScReferenceExpression)(f: PsiElement => Unit): Unit = {
    f(ref)

    val element = ref.resolve()
    if (element != null) {
      val patternDefinition = getScPatternDefinition(element)
      if (patternDefinition != null) {
        processPatternDefinition(patternDefinition)(f)
      }
    }
  }

  def processTypedStmt(typedStmt: ScTypedStmt)(f: PsiElement => Unit): Unit = {
    f(typedStmt)

    typedStmt.expr match {
      case seqCall: ScMethodCall => processMethodCall(seqCall)(f)
      case _ =>
    }
  }


  private def getSettings(patternDefinition: ScPatternDefinition): Seq[ScMethodCall] = {
    var res: Seq[ScMethodCall] = List()

    def visit(pd: ScalaPsiElement): Unit = {
      pd.acceptChildren(new ScalaElementVisitor {
        override def visitMethodCallExpression(call: ScMethodCall): Unit = {
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SETTINGS => res ++= Seq(call)
            case _ =>
          }

          visit(call.getEffectiveInvokedExpr)
          super.visitMethodCallExpression(call)
        }
      })
    }

    visit(patternDefinition)

    res
  }
}
