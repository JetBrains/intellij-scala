package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
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

    def findDependencyOrAnnotate(info: ArtifactInfo): Unit = {
      val resolversToUse = SbtResolverUtils.getProjectResolvers(Option(ScalaPsiUtil.fileContext(element)))
      val indexManager = SbtResolverIndexesManager()
      val indexes = resolversToUse.flatMap(indexManager.find).toSet
      if (indexes.isEmpty) return

      val isInRepo = {
        if (isDynamicVersion(info.version))
          indexes.exists(_.versions(info.group, info.artifact).nonEmpty)
        else
          indexes.exists(_.versions(info.group, info.artifact).contains(info.version))
      }
      if (!isInRepo) {
        val annotation = holder.createErrorAnnotation(element, SbtBundle("sbt.annotation.unresolvedDependency"))
        annotation.registerFix(new SbtUpdateResolverIndexesQuickFix)
        annotation.registerFix(new SbtRefreshProjectQuickFix)
      }
    }

    for {
      literal@ScLiteral(_) <- Option(element)
      parentExpr@ScInfixExpr(leftPart, operation, _) <- Option(literal.getParent)
      if isOneOrTwoPercents(operation)
    } yield leftPart match {
      case _: ScLiteral =>
        extractArtifactInfo(parentExpr.getParent).foreach(findDependencyOrAnnotate)
      case leftExp: ScInfixExpr if isOneOrTwoPercents(leftExp.operation) =>
        extractArtifactInfo(parentExpr).foreach(findDependencyOrAnnotate)
      case _ => // do nothing
    }
  }


  private def isOneOrTwoPercents(op: ScReferenceExpression) =
    op.getText == "%" || op.getText == "%%"

  private def extractArtifactInfo(from: PsiElement): Option[ArtifactInfo] = {
    val scalaVersion = from.scalaLanguageLevel.map(_.version)
    for {
      ScInfixExpr(leftPart, _, maybeVersion) <- Option(from)
      ScInfixExpr(maybeGroup, maybePercents, maybeArtifact) <- Option(leftPart)
      ScLiteralImpl.string(version) <- Option(maybeVersion)
      ScLiteralImpl.string(group) <- Option(maybeGroup)
      ScLiteralImpl.string(artifact) <- Option(maybeArtifact)
      shouldAppendScalaVersion = maybePercents.getText == "%%"
    } yield {
      if (shouldAppendScalaVersion && scalaVersion.isDefined)
        ArtifactInfo(group, artifact + "_" + scalaVersion.get, version)
      else
        ArtifactInfo(group, artifact, version)
    }
  }

  private def isDynamicVersion(version: String): Boolean =
    version.startsWith("latest") || version.endsWith("+") || "[]()".exists(version.contains(_))
}

