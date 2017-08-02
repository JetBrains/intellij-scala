package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/21/17.
  */
object AddSbtDependencyUtils {
  val libraryDependencies: String = "libraryDependencies"

  def getPossiblePlacesToAddFromLibraryDependencies(libDeps: ScInfixExpr): Seq[ScMethodCall] = {
    var res: Seq[ScMethodCall] = List()

    @scala.annotation.tailrec
    def getScPatternDefinition(psiElement: PsiElement): ScPatternDefinition = {
      psiElement match {
        case pattern: ScPatternDefinition => pattern
        case _: PsiFile => null
        case _ => getScPatternDefinition(psiElement.getParent)
      }
    }

    def processInfix(infix: ScInfixExpr): Unit = {
      def process(expr: ScExpression): Unit = {
        expr match {
          case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => res ++= Seq(call)
          case infix: ScInfixExpr => processInfix(infix)
          case ref: ScReferenceExpression => processReferenceExpr(ref)
          case _ =>
        }
      }

      if (infix.operation.refName == "++") {
        process(infix.lOp)
        process(infix.rOp)
      }
    }

    def processReferenceExpr(ref: ScReferenceExpression): Unit = {
      val element = ref.resolve()
      if (element != null) {
        val patternDefinition = getScPatternDefinition(element)
        val canonicalText = patternDefinition.getType().get.canonicalText
        if (canonicalText == "_root_.scala.collection.Seq[_root_.sbt.ModuleID]" || canonicalText == "scala.Seq[_root_.sbt.ModuleID]") {
          if (patternDefinition.expr.isEmpty)
            return

          patternDefinition.expr.get match {
            case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => res ++= Seq(call)
            case infix: ScInfixExpr => processInfix(infix)
            case _ =>
          }
        }
      }
    }

    if (libDeps.operation.refName == "++=" || libDeps.operation.refName == ":=") {
      libDeps.rOp match {
        case ref: ScReferenceExpression =>
          processReferenceExpr(ref)
        case infix: ScInfixExpr => processInfix(infix)
        case _ =>
      }
    }

    res
  }

  def getLibraryDepenciesInsideSettings(settings: ScMethodCall): Seq[ScInfixExpr] = {
    var args = settings.args.exprsArray

    val optCall: Option[ScMethodCall] =
      if (args.length == 1) {
        args(0) match {
          case typedStmt: ScTypedStmt if typedStmt.isSequenceArg =>
            typedStmt.expr match {
              case call: ScMethodCall => Some(call)
              case _ => None
            }
          case _ => None
        }
      } else {
        None
      }

    args = if (optCall.isDefined) optCall.get.args.exprsArray else args

    args.filter({
      case infix: ScInfixExpr if infix.lOp.getText == libraryDependencies => true
      case _ => false
    }).map(f => f.asInstanceOf[ScInfixExpr])
  }

  def getSettings(patternDefinition: ScPatternDefinition): Seq[ScMethodCall] = {
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

  def getTopLevelSbtProjects(psiSbtFile: ScalaFile): Seq[ScPatternDefinition] = {
    var res: Seq[ScPatternDefinition] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
        if (pat.expr.isEmpty)
          return

        if (pat.expr.get.getType().get.canonicalText != "_root_.sbt.Project")
          return

        res = res ++ Seq(pat)
        super.visitPatternDefinition(pat)
      }
    })

    res
  }

  def getTopLevelLibraryDependencies(psiSbtFile: ScalaFile): Seq[ScInfixExpr] = {
    var res: Seq[ScInfixExpr] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        // Looking for the top level libraryDependencies element
        if (infix.lOp.getText == libraryDependencies && infix.getParent.isInstanceOf[PsiFile]) {
          res = res ++ Seq(infix)
        }
      }
    })

    res
  }

  def addDependency(expr: PsiElement, info: ArtifactInfo)(implicit project: Project): Unit = {
    expr match {
      case e: ScInfixExpr if e.lOp.getText == libraryDependencies => processLibraryDependencies(e, info)
      case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => processSeq(call, info)
      case settings: ScMethodCall =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == "settings" =>
            addDependencyToSettings(settings, info)(project)
          case _ =>
        }
      case _ =>
    }
  }

  def addDependencyToSettings(settings: ScMethodCall, info: ArtifactInfo)(implicit project: Project): Unit = {
    doInSbtWriteCommandAction({
      settings.args.addExpr(generateLibraryDependency(info)(project))
    }, settings.getContainingFile)(project)
  }

  def processSeq(call: ScMethodCall, info: ArtifactInfo)(implicit project: Project): Unit = {
    doInSbtWriteCommandAction({
      call.args.addExpr(generateArtifactPsiExpression(info))
    }, call.getContainingFile)
  }

  def processLibraryDependencies(infix: ScInfixExpr, info: ArtifactInfo)(implicit project: Project): Unit = {
    val psiFile = infix.getContainingFile
    val opName = infix.operation.refName

    if (opName == "+=") {
      val dependency: ScExpression = infix.rOp
      val seqCall: ScMethodCall = generateSeqPsiMethodCall(info)(project)

      doInSbtWriteCommandAction({
        seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
        seqCall.args.addExpr(generateArtifactPsiExpression(info)(project))
        infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(project))
        dependency.replace(seqCall)
      }, psiFile)(project)
    } else if (opName == "++=") {
      val dependencies: ScExpression = infix.rOp
      dependencies match {
        case call: ScMethodCall =>
          val text = call.deepestInvokedExpr.getText
          // TODO: Add support for more other collections
          if (text == "Seq") {
            doInSbtWriteCommandAction({
              call.args.addExpr(generateArtifactPsiExpression(info)(project))
            }, psiFile)(project)
          }
        case _ =>
      }
    }
  }

  private def doInSbtWriteCommandAction(f: => Unit, psiSbtFile: PsiFile)(implicit project: ProjectContext): Unit = {
    new WriteCommandAction[Unit](project, psiSbtFile) {
      override def run(result: Result[Unit]): Unit = {
        f
      }
    }.execute()
  }

  private def generateSeqPsiMethodCall(info: ArtifactInfo)(implicit ctx: ProjectContext): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText("Seq()").asInstanceOf[ScMethodCall]

  private def generateLibraryDependency(info: ArtifactInfo)(implicit ctx: ProjectContext): ScInfixExpr =
    ScalaPsiElementFactory.createElementFromText(s"$libraryDependencies += ${generateArtifactText(info)}").asInstanceOf[ScInfixExpr]

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  private def generateArtifactText(info: ArtifactInfo): String =
    "\"" + s"${info.groupId}" + "\" % \"" + s"${info.artifactId}" + "\" % \"" + s"${info.version}" + "\""
}
