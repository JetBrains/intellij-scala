package org.jetbrains.plugins.scala
package project
package template

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import javax.swing.JComponent
import org.apache.ivy.util.MessageLogger
import org.jetbrains.plugins.scala.components.libextensions.ProgressIndicatorLogger

/**
  * @author Pavel Fatin
  */
final class VersionDialog(parent: JComponent) extends VersionDialogBase(parent) {

  {
    init()

    setTitle("Download")
    myVersion.setTextRenderer(Version.abbreviate)

    Versions.Scala().versions match {
      case Array() =>
        Messages.showErrorDialog(
          createCenterPanel(),
          "No versions available for download",
          s"Error Downloading Scala libraries"
        )
      case versions => myVersion.setItems(versions)
    }
  }

  def showAndGetSelected(): Option[String] =
    if (showAndGet()) {
      val version = myVersion.getSelectedItem.asInstanceOf[String]

      extensions.withProgressSynchronouslyTry(s"Downloading Scala $version", canBeCanceled = true) { manager =>
        import DependencyManagerBase._
        val dependencyManager = new DependencyManagerBase {
          override def createLogger: MessageLogger = new ProgressIndicatorLogger(manager.getProgressIndicator)
          override val artifactBlackList: Set[String] = Set.empty
        }
        val compiler        = "org.scala-lang" % "scala-compiler" % version transitive()
        val librarySources  = "org.scala-lang" % "scala-library" % version % Types.SRC
        dependencyManager.resolve(compiler)
        dependencyManager.resolveSingle(librarySources)
      }.recover {
        case _: ProcessCanceledException => // downloading aborted by user
        case exception => Messages.showErrorDialog(
          parent,
          exception.getMessage,
          s"Error downloading Scala $version"
        )
      }.map { _ =>
        version
      }.toOption
    } else None
}
