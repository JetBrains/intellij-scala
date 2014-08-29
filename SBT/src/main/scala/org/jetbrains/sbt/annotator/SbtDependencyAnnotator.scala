package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.sbt.annotator.quickfix.{SbtRefreshProjectQuickFix, SbtUpdateResolverIndexesQuickFix}
import org.jetbrains.sbt.resolvers.{SbtResolverIndexesManager, SbtResolverUtils}


/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
class SbtDependencyAnnotator extends Annotator {

  private case class ArtifactInfo(group: String, artifact: String, version: String)

  def annotate(element: PsiElement, holder: AnnotationHolder) {

    if (ScalaPsiUtil.fileContext(element).getFileType.getName != Sbt.Name) return

    def isValidOperation(op: ScReferenceExpression) = op.getText == "%" || op.getText == "%%"

    def extractInfo(from: PsiElement): Option[ArtifactInfo] =
      for {
        ScInfixExpr(lexpr, _, rOp) <- Option(from)
        ScInfixExpr(llOp, _, lrOp) <- Option(lexpr)
        ScLiteralImpl.string(version)  <- Option(rOp)
        ScLiteralImpl.string(group)    <- Option(llOp)
        ScLiteralImpl.string(artifact) <- Option(lrOp)
      } yield ArtifactInfo(group, artifact, version)

    def doAnnotate(info: Option[ArtifactInfo]): Unit = info match {
      case Some(ArtifactInfo(group, artifact, version)) =>
        val resolversToUse = SbtResolverUtils.getProjectResolvers(Option(ScalaPsiUtil.fileContext(element)))
        val indexManager = SbtResolverIndexesManager()
        val indexes = resolversToUse.flatMap(indexManager.find).toSet
        if (indexes.isEmpty) return

        val isInRepo = indexes.map { index =>
          index.versions(group, artifact).contains(version)
        }.fold(false) { (a,b) => a || b }
        if (!isInRepo) {
          val annotation = holder.createErrorAnnotation(element, SbtBundle("sbt.annotation.unresolvedDependency"))
          annotation.registerFix(new SbtUpdateResolverIndexesQuickFix)
          annotation.registerFix(new SbtRefreshProjectQuickFix)
        }
      case _ => // do nothing
    }

    for {
      lit@ScLiteral(_) <- Option(element)
      parentExpr@ScInfixExpr(lOp, operation, _) <- Option(lit.getParent)
      if isValidOperation(operation)
    } yield lOp match {
      case _: ScLiteral =>
        doAnnotate(extractInfo(parentExpr.getParent))
      case leftExp: ScInfixExpr if isValidOperation(leftExp.operation) =>
        doAnnotate(extractInfo(parentExpr))
      case _ => // do nothing
    }
  }
}

