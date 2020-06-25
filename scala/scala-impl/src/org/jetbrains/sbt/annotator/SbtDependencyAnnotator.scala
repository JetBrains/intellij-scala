package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.module.{Module, ModuleManager, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.annotator.quickfix.{SbtRefreshProjectQuickFix, SbtUpdateResolverIndexesQuickFix}
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.resolvers.{ResolverException, SbtResolverUtils}

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */

class SbtDependencyAnnotator extends Annotator {

  private case class ArtifactInfo(group: String, artifact: String, version: String)

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    annotate(element)(new ScalaAnnotationHolderAdapter(holder))

  def annotate(element: PsiElement)(holder: ScalaAnnotationHolder): Unit =
    try {
      doAnnotate(element, holder)
    } catch {
      case _: ResolverException =>
        // TODO: find another way to notify user instead of spamming with notifications
        // NotificationUtil.showMessage(null, exc.getMessage)
    }

  private def doAnnotate(element: PsiElement, holder: ScalaAnnotationHolder): Unit = {
    def moduleByName(@NonNls name: String) = ModuleManager.getInstance(element.getProject).getModules.find(_.getName == name)

    def findBuildModule(module: Option[Module]): Option[Module] = module match {
      case Some(SbtModuleType(_)) => module
      case Some(m) => moduleByName(s"${m.getName}${Sbt.BuildModuleSuffix}")
      case _ => None
    }

    def findProjectModule(module: Option[Module]): Option[Module] = module match {
      case Some(SbtModuleType(m)) => moduleByName(m.getName.stripSuffix(Sbt.BuildModuleSuffix))
      case _ => module
    }

    implicit val p: Project = element.getProject

    val module = Option(ScalaPsiUtil.getModule(element))

    if (ScalaPsiUtil.fileContext(element).getFileType.getName != Sbt.Name &&
        module.exists(m => !ModuleType.get(m).isInstanceOf[SbtModuleType])) return

    def findDependencyOrAnnotate(info: ArtifactInfo): Unit = {
      val resolversToUse = SbtResolverUtils.projectResolvers(element)
      val indexes = resolversToUse.flatMap(_.getIndex(p))
      if (indexes.isEmpty) return

      val isInRepo = {
        if (isDynamicVersion(info.version))
          indexes.exists(_.searchVersion(info.group, info.artifact).nonEmpty)
        else
          indexes.exists(_.searchVersion(info.group, info.artifact).contains(info.version))
      }
      if (!isInRepo) {
        val annotation = holder.createWeakWarningAnnotation(element, SbtBundle.message("sbt.annotation.unresolvedDependency"))

        val sbtModule = findBuildModule(module)
        sbtModule.foreach { m =>
          annotation.registerFix(new SbtUpdateResolverIndexesQuickFix(m))
        }
        annotation.registerFix(new SbtRefreshProjectQuickFix)
      }
    }

    for {
      literal <- if (element.isInstanceOf[ScLiteral]) Some(element) else None
      parentExpr@ScInfixExpr(leftPart, operation, _) <- Option(literal.getParent)
      if isOneOrTwoPercents(operation)
    } yield {
      val scalaVersion = findProjectModule(module).flatMap(_.scalaLanguageLevel).map(_.getVersion)
      leftPart match {
        case _: ScLiteral =>
          extractArtifactInfo(parentExpr.getParent, scalaVersion).foreach(findDependencyOrAnnotate)
        case leftExp: ScInfixExpr if isOneOrTwoPercents(leftExp.operation) =>
          extractArtifactInfo(parentExpr, scalaVersion).foreach(findDependencyOrAnnotate)
        case _ => // do nothing
      }
    }
  }


  private def isOneOrTwoPercents(op: ScReferenceExpression) =
    op.textMatches("%") || op.textMatches("%%")

  private def extractArtifactInfo(from: PsiElement, scalaVersion: Option[String]): Option[ArtifactInfo] = {
    for {
      ScInfixExpr(leftPart, _, maybeVersion) <- Option(from)
      ScInfixExpr(maybeGroup, maybePercents, maybeArtifact) <- Option(leftPart)
      ScStringLiteral(version) <- Option(maybeVersion)
      ScStringLiteral(group) <- Option(maybeGroup)
      ScStringLiteral(artifact) <- Option(maybeArtifact)
      shouldAppendScalaVersion = maybePercents.textMatches("%%")
    } yield {
      if (shouldAppendScalaVersion && scalaVersion.isDefined)
        ArtifactInfo(group, artifact + "_" + scalaVersion.get, version)
      else
        ArtifactInfo(group, artifact, version)
    }
  }

  private def isDynamicVersion(@NonNls version: String): Boolean =
    version.startsWith("latest") || version.endsWith("+") || "[]()".exists(version.contains(_))
}

