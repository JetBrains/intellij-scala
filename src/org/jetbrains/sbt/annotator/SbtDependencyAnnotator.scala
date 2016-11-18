package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.module.{ModuleManager, ModuleType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.sbt.annotator.quickfix.{SbtRefreshProjectQuickFix, SbtUpdateResolverIndexesQuickFix}
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.resolvers.{ResolverException, SbtResolverUtils}

import scala.util.Try

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */

class SbtDependencyAnnotator extends Annotator {

  private case class ArtifactInfo(group: String, artifact: String, version: String)

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    try {
      doAnnotate(element, holder)
    } catch {
      case exc: ResolverException =>
        // TODO: find another way to notify user instead of spamming with notifications
        // NotificationUtil.showMessage(null, exc.getMessage)
    }

  private def doAnnotate(element: PsiElement, holder: AnnotationHolder): Unit = {

    implicit val p = element.getProject


    lazy val module = Option(ScalaPsiUtil.getModule(element))
    lazy val sbtModule = module.flatMap(m=>ModuleManager.getInstance(p).getModules.find(_.getName == s"${m.getName}-build"))

    if (ScalaPsiUtil.fileContext(element).getFileType.getName != Sbt.Name &&
        module.exists(m => !ModuleType.get(m).isInstanceOf[SbtModuleType])) return

    def findDependencyOrAnnotate(info: ArtifactInfo): Unit = {
      val resolversToUse = SbtResolverUtils.getProjectResolversForFile(Option(ScalaPsiUtil.fileContext(element)))
      val indexes = resolversToUse.map(_.getIndex(p))
      if (indexes.isEmpty) return

      val isInRepo = {
        if (isDynamicVersion(info.version))
          indexes.exists(_.searchVersion(info.group, info.artifact).nonEmpty)
        else
          indexes.exists(_.searchVersion(info.group, info.artifact).contains(info.version))
      }
      if (!isInRepo) {
        val annotation = holder.createWeakWarningAnnotation(element, SbtBundle("sbt.annotation.unresolvedDependency"))

        if (module.exists(ModuleType.get(_).isInstanceOf[SbtModuleType])) {
          annotation.registerFix(new SbtUpdateResolverIndexesQuickFix(module.get))
        } else if (sbtModule.isDefined) {
          annotation.registerFix(new SbtUpdateResolverIndexesQuickFix(sbtModule.get))
        }
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

