package org.jetbrains.plugins.scala
package project
package template

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import org.apache.ivy.util.MessageLogger
import org.jetbrains.plugins.scala.components.libextensions.ProgressIndicatorLogger
import org.jetbrains.plugins.scala.extensions.withProgressSynchronouslyTry

import javax.swing.JComponent

/**
 * @author Pavel Fatin
 */
final class VersionDialog(parent: JComponent) extends VersionDialogBase(parent) {

  {
    init()

    setTitle(ScalaBundle.message("title.download"))
    myVersion.setTextRenderer(Version.abbreviate)

    val versions = Versions.Scala.loadVersionsWithProgress().versions
    if (versions.nonEmpty) {
      myVersion.setItems(versions)
      // While Scala 3 support is WIP we do not want preselect Scala 3 version
      val selectIndex = versions.indexWhere(v => !v.startsWith("3"))
      if (selectIndex >= 0) {
        myVersion.setSelectedIndex(selectIndex)
      }
    }
    else
      Messages.showErrorDialog(
        createCenterPanel(),
        ScalaBundle.message("no.versions.available.for.download"),
        ScalaBundle.message("title.error.downloading.scala.libraries")
      )
  }

  def showAndGetSelected(): Option[String] =
    if (showAndGet()) {
      val scalaVersionStr = myVersion.getSelectedItem.asInstanceOf[String]
      val scalaVersion = ScalaVersion.fromString(scalaVersionStr).getOrElse {
        Messages.showErrorDialog(
          parent,
          ScalaBundle.message("invalid.scala.version.format", scalaVersionStr),
          ScalaBundle.message("error.downloading.scala.version", scalaVersionStr)
        )
        return None
      }

      withProgressSynchronouslyTry(ScalaBundle.message("downloading.scala.version", scalaVersion), canBeCanceled = true) { manager =>
        import DependencyManagerBase._
        val dependencyManager = new DependencyManagerBase {
          override def createLogger: MessageLogger = new ProgressIndicatorLogger(manager.getProgressIndicator)
          override val artifactBlackList: Set[String] = Set.empty
        }

        val (compiler, librarySources) = (
          DependencyDescription.scalaArtifact("compiler", scalaVersion).transitive(),
          // scala3-library has also dependency on scala-library, so set transitive
          DependencyDescription.scalaArtifact("library", scalaVersion).transitive().sources(),
        )
        dependencyManager.resolve(compiler, librarySources)
        ()
      }.recover {
        case _: ProcessCanceledException => // downloading aborted by user
        case exception =>
          //noinspection ReferencePassedToNls
          Messages.showErrorDialog(
            parent,
            exception.getMessage,
            ScalaBundle.message("error.downloading.scala.version", scalaVersionStr)
          )
      }.map { _ =>
        scalaVersion.minor
      }.toOption
    } else None

}
