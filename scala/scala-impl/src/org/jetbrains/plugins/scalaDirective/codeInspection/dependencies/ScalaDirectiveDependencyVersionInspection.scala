package org.jetbrains.plugins.scalaDirective.codeInspection.dependencies

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.completion.condition
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

final class ScalaDirectiveDependencyVersionInspection extends DependencyVersionInspection {
  override protected def isAvailable(element: PsiElement): Boolean =
    ScalaDirectiveDependencyVersionInspection.pattern.accepts(element)

  override protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] =
    ScalaDirectiveDependencyVersionInspection.getDependencyDescriptor(element)

  override protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix =
    new ScalaDirectiveUpdateDependencyVersionQuickFix(element, newerVersion)
}

object ScalaDirectiveDependencyVersionInspection {
  private val dependencyDirectiveKeys = Set(
    "dep", "deps", "dependencies",
    "test.dep", "test.deps", "test.dependencies",
    "compileOnly.dep", "compileOnly.deps", "compileOnly.dependencies",
  )

  private val pattern = psiElement()
    .withElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)
    .inside(
      psiElement(classOf[ScDirective])
        .`with`(condition[ScDirective]("scalaDirectiveWithDepKey")(directive =>
          directive.key.exists(key => dependencyDirectiveKeys(key.getText))))
    )

  private[dependencies] def getDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] = element match {
    case ElementText(ScalaDirectiveDependencyDescriptor(descriptor)) => Some(descriptor)
    case _ => None
  }

  private[dependencies] object ScalaDirectiveDependencyDescriptor {
    def unapply(dependencyText: String): Option[DependencyDescriptor] = {
      val tokens = dependencyText.split(":", -1).map(s => Option.when(s.nonEmpty)(s))
      tokens match {
        // org:artifact:version
        case Array(Some(groupId), Some(artifactId), Some(version)) =>
          Some(DependencyDescriptor(groupId, artifactId, version))
        // org::artifact:version
        case Array(Some(groupId), None, Some(artifactId), Some(version)) =>
          Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.ScalaVersion))
        // org::artifact::version
        case Array(Some(groupId), None, Some(artifactId), None, Some(version)) =>
          Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.ScalaVersion, platform = true))
        // org:::artifact:version
        case Array(Some(groupId), None, None, Some(artifactId), Some(version)) =>
          Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.FullScalaVersion))
        // org:::artifact::version
        case Array(Some(groupId), None, None, Some(artifactId), None, Some(version)) =>
          Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.FullScalaVersion, platform = true))
        case _ => None
      }
    }

    /** Convert dependency descriptor to directive dependency value presentation */
    def render(descriptor: DependencyDescriptor): String = {
      val groupArtifactSeparator = descriptor.artifactIdSuffix match {
        case ArtifactIdSuffix.Empty => ":"
        case ArtifactIdSuffix.ScalaVersion => "::"
        case ArtifactIdSuffix.FullScalaVersion => ":::"
      }
      val artifactVersionSeparator = if (descriptor.platform) "::" else ":"
      s"${descriptor.groupId}$groupArtifactSeparator${descriptor.artifactId}$artifactVersionSeparator${descriptor.version}"
    }
  }
}
