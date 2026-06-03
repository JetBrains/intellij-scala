package org.jetbrains.sbt.codeInspection

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.sbt.language.completion.SBT_ORG_ARTIFACT
import org.jetbrains.sbt.language.utils.SbtDependencyUtils

object SbtCodeInspectionUtils {
  def isAvailable(element: PsiElement): Boolean = element.getParent match {
    case infix: ScInfixExpr if infix.isValid =>
      SBT_ORG_ARTIFACT.contains(infix.left.`type`().getOrAny.canonicalText) && element == infix.right
    case _ => false
  }

  def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] = element.getParent match {
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
}
