package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
  * Created by afonichkin on 8/28/17.
  */
object SbtDependenciesVisitor {
  val libraryDependencies: String = "libraryDependencies"

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
        case infix: ScInfixExpr if infix.lOp.getText == libraryDependencies => processInfix(infix)(f)
        case call: ScMethodCall => processMethodCall(call)(f)
        case ref: ScReferenceExpression => processReferenceExpr(ref)(f)
        case _ =>
      })
    }

    if (call.deepestInvokedExpr.getText == "Seq") {

      // Probably makes more sense to move it upper
      val formalSeq: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.scala.collection.Seq", call, call).get
      val formalSetting: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.sbt.Def.Setting", call, call).get
      // Can be of type ModuleId and of type Setting
      // We have to somethow distinguish between 2 different type of sequences
      // process seq
      call.getType().get match {
        case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSeq) =>
          val args = parameterized.typeArguments
          if (args.length == 1) {
            args.head match {
              case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSetting) =>
                processSettings(call)
              case _ =>
            }
          }
        case _ =>
      }

      val canonicalText = call.getType().get.canonicalText
      if (canonicalText == "_root_.scala.collection.Seq[_root_.sbt.ModuleID]" || canonicalText == "scala.Seq[_root_.sbt.ModuleID]") {
        // Actually, don't have to do here anything, because it just a list of differet modules
      } else {
        // TODO: have to put it clearly that it will be Seq[Setting], for now we just assume it
        val x = 1
      }


    } else {
      call.getEffectiveInvokedExpr match {
        case expr: ScReferenceExpression if expr.refName == "settings" =>
          processSettings(call)
        case _ =>
      }

    }
  }

  def processPatternDefinition(patternDefinition: ScPatternDefinition)(f: PsiElement => Unit): Unit = {
    f(patternDefinition)

    if (patternDefinition.getType().get.canonicalText == "_root_.sbt.Project") {
      val settings = getSettings(patternDefinition)
      settings.foreach(processMethodCall(_)(f))
    } else {
      if (patternDefinition.expr.isEmpty)
        return

      patternDefinition.expr.get match {
        case call: ScMethodCall => processMethodCall(call)(f)
        case infix: ScInfixExpr => processInfix(infix)(f)
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
      process(infix.lOp)
      process(infix.rOp)
    } else if (infix.operation.refName == "++=") {
      process(infix.rOp)
    } else if (infix.operation.refName == ":=") {
      process(infix.rOp)
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
            case expr: ScReferenceExpression if expr.refName == "settings" => res ++= Seq(call)
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
