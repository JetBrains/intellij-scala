package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/21/17.
  */
object AddSbtDependencyUtils {

  val libraryDependencies: String = "libraryDependencies"

  // TODO: process all the stuff inside one call of "acceptChildren", or find way to stop processing
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

  def addDependency(expr: PsiElement, info: ArtifactInfo, psiFile: PsiFile)(implicit project: Project): Unit = {
    expr match {
      case e: ScInfixExpr if e.lOp.getText == libraryDependencies => processLibraryDependencies(e, info, psiFile)
      case _ =>
    }
  }

  def processLibraryDependencies(infix: ScInfixExpr, info: ArtifactInfo, psiFile: PsiFile)(implicit project: Project): Unit = {
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

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  private def generateArtifactText(info: ArtifactInfo): String =
    "\"" + s"${info.groupId}" + "\" % \"" + s"${info.artifactId}" + "\" % \"" + s"${info.version}" + "\""
}
