package org.jetbrains.sbt.codeInspection

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions.{&&, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.language.completion.SBT_ORG_ARTIFACT
import org.jetbrains.sbt.language.utils.{CustomPackageSearchApiHelper, CustomPackageSearchParams, SbtExtendedArtifactInfo, SbtDependencyUtils}

import java.util.concurrent.ConcurrentLinkedDeque
import scala.collection.mutable

class SbtDependencyVersionInspection extends AbstractRegisteredInspection{

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = try {

    val versions: mutable.HashSet[String] = mutable.HashSet.empty

    element.getParent match {
      case infix: ScInfixExpr
        if SBT_ORG_ARTIFACT.contains(infix.left.`type`().getOrAny.canonicalText)
          && element == infix.right =>
        val libDepList = inReadAction(SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(infix, mode = SbtDependencyUtils.GetMode.GetDep))
        if (libDepList.isEmpty) return None
        val libDepExprTuple = libDepList.head.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)]
        val libDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(
          libDepExprTuple).map(_.asInstanceOf[String])

        if (libDep.length < 3) return None

        val scalaVers = SbtDependencyUtils.getAllScalaVersionsOrDefault(element)

        val groupId = libDep(0)
        val artifactId = libDep(1)

        val version = libDep(2)

        var newerStableVersion = version

        if (groupId == null || groupId.isEmpty || artifactId == null || artifactId.isEmpty) return None

        val cld = new ConcurrentLinkedDeque[SbtExtendedArtifactInfo]()

        def addVersion(groupId: String, artifactId: String): Unit = {
          val searchFuture = CustomPackageSearchApiHelper.searchDependencyVersions(groupId, artifactId, CustomPackageSearchParams(useCache = true), cld)
          CustomPackageSearchApiHelper
            .waitAndAdd(
              searchFuture,
              cld,
              (lib: SbtExtendedArtifactInfo) => lib.versions.foreach(item =>
                versions.add(item))
            )
        }

        if (!SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)) {
          addVersion(groupId, artifactId)
        }
        else {
          scalaVers.foreach(scalaVer => addVersion(groupId, SbtDependencyUtils.buildScalaDependencyString(artifactId, scalaVer)))
        }

        versions.foreach(ver => {
          if (SbtDependencyUtils.isVersionStable(ver) && SbtDependencyUtils.isGreaterStableVersion(ver, newerStableVersion)) {
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
  } catch {
    case _: Exception => None
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