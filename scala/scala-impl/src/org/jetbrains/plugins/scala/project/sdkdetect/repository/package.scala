package org.jetbrains.plugins.scala.project.sdkdetect

import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

package object repository {

  sealed trait CompilerClasspathResolveFailure {
    import CompilerClasspathResolveFailure._

    // used to print to logs
    def errorMessage: String = this match {
      case NotSupportedForScalaVersion(scalaVersion, detector) => s"resolve not supported for scala version $scalaVersion and detector ${detector.getClass.getSimpleName}"
      case UnresolvedArtifact(artifactName)                    => s"unresolved artifact: $artifactName"
      case AmbiguousArtifactsResolved(fileNames)               => s"ambiguous artifact resolved: ${fileNames.mkString(", ")}"
      case UnknownResolveIssue(resolveProblems)                => s"unknown resolve issues: ${resolveProblems.zipWithIndex.mkString(", ")}"
      case UnknownException(exception)                         => s"unknown exception: ${exception.getMessage}"
    }

    // used to display on UI

    /** @return None if the error isn't supposed to be displayed on Ui */
    def nlsErrorMessage: Option[NlsString] = {
      this match {
        case NotSupportedForScalaVersion(_, _)     => None
        case UnresolvedArtifact(artifactName)      => Some(NlsString(ScalaBundle.message("unresolved.artifact", artifactName)))
        case AmbiguousArtifactsResolved(fileNames) => Some(NlsString(ScalaBundle.message("ambiguous.artifact.resolved", fileNames.mkString(", "))))
        case UnknownResolveIssue(resolveProblems)  => Some(NlsString(ScalaBundle.message("unknown.resolve.issues", resolveProblems.zipWithIndex.mkString(", "))))
        case UnknownException(exception)           => Some(NlsString(ScalaBundle.message("unknown.exception", exception.getMessage)))
      }
    }
  }

  object CompilerClasspathResolveFailure {
    case class UnresolvedArtifact(artifactName: String) extends CompilerClasspathResolveFailure
    case class AmbiguousArtifactsResolved(fileNames: Seq[String]) extends CompilerClasspathResolveFailure
    case class NotSupportedForScalaVersion(scalaVersion: String, detector: ScalaSdkDetector) extends CompilerClasspathResolveFailure
    case class UnknownResolveIssue(resolveProblems: Seq[String]) extends CompilerClasspathResolveFailure
    case class UnknownException(exception: Throwable) extends CompilerClasspathResolveFailure
  }
}
