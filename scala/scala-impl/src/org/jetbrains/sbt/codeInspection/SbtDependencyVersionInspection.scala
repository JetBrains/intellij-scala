package org.jetbrains.sbt.codeInspection

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.{DependencySearchService, SearchParameters}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions.{&&, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.language.completion.SBT_ORG_ARTIFACT
import org.jetbrains.sbt.language.utils.PackageSearchApiHelper.waitAndAdd
import org.jetbrains.sbt.language.utils.{PackageSearchApiHelper, SbtDependencyUtils}
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep

import java.util.concurrent.ConcurrentLinkedDeque
import scala.collection.mutable.ArrayBuffer

class SbtDependencyVersionInspection extends AbstractRegisteredInspection{

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode
    val scalaVer = element.scalaLanguageLevelOrDefault.getVersion
    element.getParent match {
      case infix: ScInfixExpr
        if SBT_ORG_ARTIFACT.contains(infix.left.`type`().getOrAny.canonicalText)
          && element == infix.right =>
        val libDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(
          inReadAction(SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(infix, mode = GetDep)
          ).head.asInstanceOf[(ScExpression, String, ScExpression)]).map(_.asInstanceOf[String])

        if (libDep.length < 3) return None

        val groupId = libDep(0)
        val artifactId = libDep(1)
        val version = libDep(2)

        val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
        val dependencySearch = DependencySearchService.getInstance(element.getProject)
        val versions = ArrayBuffer[String]()
        val searchPromise = PackageSearchApiHelper.searchFullTextDependency(
          groupId,
          artifactId,
          dependencySearch,
          new SearchParameters(true, isUnitTestMode),
          cld)
        var newerStableVersion = version

        def addVersion(repo: MavenRepositoryArtifactInfo): Unit = {
          if (repo.getGroupId == groupId && (repo.getArtifactId == artifactId || repo.getArtifactId == s"${artifactId}_$scalaVer"))
            repo.getItems.foreach(item => versions += item.getVersion)
        }

        def preprocessVersion(v: String): String = {
          val pattern = """\d+""".r
          v.split("\\.").map(part => {
            val newPart = pattern.findAllIn(part).toList
            if (newPart.isEmpty) 0
            else newPart(0)
          }).mkString(".")
        }

        def isGreaterStableVersion(newVer: String, oldVer: String): Boolean = {
          val oldVerPreprocessed = preprocessVersion(oldVer)
          val newVerPreprocessed = preprocessVersion(newVer)
          newVerPreprocessed.split("\\.")
            .zipAll(oldVerPreprocessed.split("\\."), "0", "0")
            .find {case(a, b) => a != b }
            .fold(0) { case (a, b) => a.toInt - b.toInt } > 0
        }
        waitAndAdd(searchPromise, cld, addVersion)

        versions.foreach(ver => {
          if (!ver.contains('-') && isGreaterStableVersion(ver, newerStableVersion)) {
            newerStableVersion = ver
          }
        })
        if (version != newerStableVersion)
          Some(manager.createProblemDescriptor(
            element,
            SbtBundle.message("packagesearch.newer.stable.version.available", groupId, artifactId),
            isOnTheFly,
            Array(new SbtUpdateDependencyVersionQuickFix(element, newerStableVersion).asInstanceOf[LocalQuickFix]),
            highlightType))
        else None
      case _ => None
    }
  }
}

class SbtUpdateDependencyVersionQuickFix(elem: PsiElement, newVer: String)
  extends AbstractFixOnPsiElement(SbtBundle.message("packagesearch.update.dependency.to.newer.stable.version", newVer), elem) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    element match {
      case str: ScStringLiteral =>
        str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\""))
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case (_: ScReferencePattern) && inNameContext(ScPatternDefinition.expr(expr)) => expr match {
            case str: ScStringLiteral =>
              str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\""))
            case _ =>
          }
        }
      case _ => // do nothing
    }
  }
}