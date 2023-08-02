package org.jetbrains.sbt.codeInspection

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple}
import org.jetbrains.plugins.scala.extensions.{&, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.language.completion.SBT_ORG_ARTIFACT
import org.jetbrains.sbt.language.utils.{CustomPackageSearchApiHelper, CustomPackageSearchParams, SbtDependencyUtils, SbtExtendedArtifactInfo}

import java.util.concurrent.ConcurrentLinkedDeque
import scala.collection.mutable

class SbtDependencyVersionInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
    try {
      val versions: mutable.HashSet[String] = mutable.HashSet.empty
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

      element.getParent match {
        case infix: ScInfixExpr if SBT_ORG_ARTIFACT.contains(infix.left.`type`().getOrAny.canonicalText) && element == infix.right =>
          val libDepList = inReadAction(SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(infix, mode = SbtDependencyUtils.GetMode.GetDep))
          val libDepExprTuple = libDepList.head.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)]
          val libDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(libDepExprTuple).map(_.asInstanceOf[String])

          if (libDep.length >= 3) {
            val groupId = libDep.head
            val artifactId = libDep(1)
            val version = libDep(2)

            if (groupId != null && groupId.nonEmpty && artifactId != null && artifactId.nonEmpty) {
              if (!SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)) {
                addVersion(groupId, artifactId)
              }
              else {
                val scalaVers =if (SbtDependencyUtils.SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST contains s"$groupId:$artifactId")
                  SbtDependencyUtils.getAllScalaVersionsOrDefault(element) else
                  SbtDependencyUtils.getAllScalaVersionsOrDefault(element, majorOnly = true)
                scalaVers.foreach(scalaVer => addVersion(groupId, SbtDependencyUtils.buildScalaArtifactIdString(groupId, artifactId, scalaVer)))
              }

              var newerStableVersion = version

              versions.foreach(ver => {
                if (SbtDependencyUtils.isVersionStable(ver) && SbtDependencyUtils.isGreaterStableVersion(ver, newerStableVersion)) {
                  newerStableVersion = ver
                }
              })

              if (version != newerStableVersion) {
                holder.registerProblem(
                  element,
                  SbtBundle.message("packagesearch.newer.stable.version.available", groupId, artifactId),
                  new SbtUpdateDependencyVersionQuickFix(element, newerStableVersion)
                )
              }
            }
          }
        case _ =>
      }
    } catch {
      case c: ControlFlowException => throw c
      case _: Throwable =>
    }
  }
}

class SbtUpdateDependencyVersionQuickFix(elem: PsiElement, newVer: String)
  extends AbstractFixOnPsiElement(SbtBundle.message("packagesearch.update.dependency.to.newer.stable.version", newVer), elem) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    val features = ScalaFeatures.default
    element match {
      case str: ScStringLiteral =>
        str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\"", features))
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case (_: ScReferencePattern) & inNameContext(ScPatternDefinition.expr(expr)) => expr match {
            case str: ScStringLiteral =>
              str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\"", features))
            case _ =>
          }
        }
      case _ => // do nothing
    }
  }
}
