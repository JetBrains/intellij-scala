package org.jetbrains.plugins.scala.project.template

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import org.apache.ivy.util.MessageLogger
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.components.libextensions.ProgressIndicatorLogger
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, withProgressSynchronouslyTry}
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaLibrary
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.plugins.scala.project.template.ScalaVersionDownloadingDialog.{ScalaVersionResolveResult, createScalaVersionResolveResult, preselectLatestScala2Version}
import org.jetbrains.sbt.project.template.SComboBox

import java.awt.Point
import java.io.File
import javax.swing.{JComponent, JPopupMenu, JScrollPane}
import scala.util.Try

final class ScalaVersionDownloadingDialog(parent: JComponent) extends VersionDialogBase(parent) {

  {
    init()

    setTitle(ScalaBundle.message("title.download"))
    myVersion.setTextRenderer(Version.abbreviate)

    val versions = Versions.Scala.loadVersionsWithProgressDialog().versions
    if (versions.nonEmpty) {
      myVersion.setItems(versions.toArray)
      preselectLatestScala2Version(myVersion, versions)
    }
    else
      Messages.showErrorDialog(
        createCenterPanel(),
        ScalaBundle.message("no.versions.available.for.download"),
        ScalaBundle.message("title.error.downloading.scala.libraries")
      )
  }

  def showAndGetSelected(): Option[ScalaVersionResolveResult] =
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

      val tri: Try[ScalaVersionResolveResult] = withProgressSynchronouslyTry(ScalaBundle.message("downloading.scala.version", scalaVersionStr), canBeCanceled = true) { manager =>
        val dependencyManager = new DependencyManagerBase {
          override def createLogger: MessageLogger = new ProgressIndicatorLogger(manager.getProgressIndicator)
        }
        createScalaVersionResolveResult(scalaVersion, dependencyManager)
      }
      tri.fold({
        case _: ProcessCanceledException =>
          None
        case exception =>
          //noinspection ReferencePassedToNls
          Messages.showErrorDialog(
            parent,
            exception.getMessage,
            ScalaBundle.message("error.downloading.scala.version", scalaVersionStr)
          )
          None
      }, Some(_))
    }
    else None
}

object ScalaVersionDownloadingDialog {

  final case class ScalaVersionResolveResult(scalaVersion: String, compilerClassPathJars: Seq[File], librarySourcesJars: Seq[File])

  /**
   * While Scala 3 support is WIP we do not want preselect Scala 3 version
   * @param versions assumed to be sorted
   */
  private def preselectLatestScala2Version(versionComboBox: SComboBox[String], versions: Seq[String]): Unit = {
    val selectIndex = versions.indexWhere(v => !v.startsWith("3"))
    if (selectIndex >= 0) {
      versionComboBox.setSelectedIndex(selectIndex)

      if (selectIndex > 0) {
        // without this, Scala 3 versions will be hard to notice, because by default Swing scrolls to the selected item
        UiUtils.scrollToTheTop(versionComboBox)
      }
    }
  }

  @VisibleForTesting
  def createScalaVersionResolveResult(scalaVersion: ScalaVersion, dependencyManager: DependencyManagerBase): ScalaVersionResolveResult = {
    import org.jetbrains.plugins.scala.DependencyManagerBase._

    val (compiler, librarySources) = (
      DependencyDescription.scalaArtifact("compiler", scalaVersion).transitive(),
      // Because of scala3-library there is no need to add transitive in "library" kind, because scala2-library is already downloaded from "compiler" kind (because it is marked as transitive)
      DependencyDescription.scalaArtifact("library", scalaVersion).sources(),
    )
    val compilerClasspathResolveResult = dependencyManager.resolve(compiler)
    // ATTENTION:
    // Sources jars should be resolved in a separate request to dependency manager
    // Otherwise resolved jars will contain only "sources" jar, without "classes"
    val librarySourcesResolveResult = dependencyManager.resolve(librarySources)

    def getScala2LibrarySources: Seq[ResolvedDependency] = {
      val scala2Library = compilerClasspathResolveResult.filter(_.file.getName.startsWith(ScalaLibrary.prefix))
      val scala2VersionString = scala2Library.head.info.version
      ScalaVersion.fromString(scala2VersionString).map { scala2Version =>
        val scala2LibrarySources = DependencyDescription.scalaArtifact("library", scala2Version).sources()
        dependencyManager.resolve(scala2LibrarySources)
      }.getOrElse(Seq.empty)
    }

    // It is necessary to download scala2-library sources explicitly.
    // At least I couldn't find a way to force Ivy to download sources of transitive dependencies.
    val scala2LibraryDependency =
      if (scalaVersion.isScala3) getScala2LibrarySources
      else Seq.empty

    ScalaVersionResolveResult(
      scalaVersion.minor,
      compilerClasspathResolveResult.map(_.file),
      (librarySourcesResolveResult ++ scala2LibraryDependency).map(_.file),
    )
  }
  object UiUtils {

    def scrollToTheTop(versionComboBox: SComboBox[_]): Unit = {
      for {
        scrollPane <- findPopupScrollPane(versionComboBox)
      } scrollToTop(scrollPane)
    }

    private def findPopupScrollPane(comboBox: SComboBox[_]): Option[JScrollPane] =
      for {
        popup <- findPopupMenu(comboBox)
        scrollPane <- findScrollPane(popup)
      } yield scrollPane

    private def findPopupMenu(versionComboBox: SComboBox[_]): Option[JPopupMenu] = {
      val ui = versionComboBox.getUI
      val children = Iterable.tabulate(ui.getAccessibleChildrenCount(versionComboBox))(ui.getAccessibleChild(versionComboBox, _))
      children.findByType[JPopupMenu]
    }

    private def findScrollPane(popup: JPopupMenu): Option[JScrollPane] = {
      val children = Iterable.tabulate(popup.getComponentCount)(popup.getComponent)
      children.findByType[JScrollPane]
    }

    private def scrollToTop(scrollPane: JScrollPane): Unit = {
      val viewport = scrollPane.getViewport

      // NOTE: without this call `setViewPosition` will do nothing until user manually scrolls the viewport
      // (though in general `setSize` shouldn't be called manually)
      viewport.setSize(viewport.getPreferredSize)

      viewport.setViewPosition(new Point(0, 0))
    }
  }
}
