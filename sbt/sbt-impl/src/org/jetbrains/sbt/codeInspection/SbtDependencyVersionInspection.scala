package org.jetbrains.sbt.codeInspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, OptionExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.language.completion.SBT_ORG_ARTIFACT
import org.jetbrains.sbt.language.utils.SbtDependencyUtils

class SbtDependencyVersionInspection extends DependencyVersionInspection {
  override protected def isAvailable(element: PsiElement): Boolean = element.getParent match {
    case infix: ScInfixExpr if infix.isValid =>
      SBT_ORG_ARTIFACT.contains(infix.left.`type`().getOrAny.canonicalText) && element == infix.right
    case _ => false
  }

  override protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] = element.getParent match {
    case infix: ScInfixExpr =>
      // TODO: it is not clear whether there could be any exceptions.
      //       Probably NPE somewhere in processLibraryDependencyFromExprAndString?
      //       Needs **global** refactoring of SbtDependencyUtils
      try {
        val libDepList = SbtDependencyUtils.getLibraryDependenciesOrPlacesFromPsi(infix, mode = SbtDependencyUtils.GetMode.GetDep)
        val maybeLibDepExprTuple =
          libDepList.headOption.filterByType[(ScInfixExpr, String, ScInfixExpr)]

        maybeLibDepExprTuple.flatMap { libDepExprTuple =>
          val libDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(libDepExprTuple)
          libDep.toOption.collect {
            case (groupId: String) :: (artifactId: String) :: (version: String) :: _
              if groupId != null && groupId.nonEmpty && artifactId != null && artifactId.nonEmpty =>

              val needsScalaVersionSuffix = SbtDependencyUtils.isScalaLibraryDependency(libDepExprTuple._1)

              val artifactIdSuffix = if (needsScalaVersionSuffix) {
                val needsFullScalaVersion =
                  SbtDependencyUtils.SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST.contains(s"$groupId:$artifactId")

                if (needsFullScalaVersion) ArtifactIdSuffix.FullScalaVersion
                else ArtifactIdSuffix.ScalaVersion
              } else ArtifactIdSuffix.Empty

              DependencyDescriptor(groupId, artifactId, Option.when(!version.isBlank)(version), artifactIdSuffix)
          }
        }
      } catch {
        case c: ControlFlowException => throw c
        case _: Throwable => None
      }
    case _ => None
  }

  override protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix =
    new SbtUpdateDependencyVersionQuickFix(element, newerVersion)
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
