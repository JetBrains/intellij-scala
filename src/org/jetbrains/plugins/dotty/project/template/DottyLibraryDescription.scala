package org.jetbrains.plugins.dotty.project.template

import java.io.File
import java.util
import javax.swing.JComponent

import com.intellij.framework.library.DownloadableLibraryType
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import org.jetbrains.plugins.dotty.project.{DottyLibraryKind, DottyVersions}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.template._

/**
  * @author adkozlov
  */
object DottyLibraryDescription extends ScalaLibraryDescription {

  override protected val libraryKind = DottyLibraryKind

  override protected val sdkDescriptor = DottySdkDescriptor

  override protected def ivySdks = sdksIn(Seq(ivyRepository / "me.d-d", ivyRepository / "jline", ivyScalaRoot))

  override protected def mavenSdks = sdksIn(Seq(mavenRepository / "me" / "d-d",
    mavenRepository / "jline" / "jline", mavenScalaRoot))

  override def dialog(parentComponent: JComponent, provider: () => util.List[SdkChoice]) = {
    new DottySdkSelectionDialog(parentComponent, provider)
  }

  override def getDefaultLevel: LibraryLevel = LibraryLevel.GLOBAL

  private def sdksIn(roots: Seq[File]): Seq[SdkDescriptor] = {
    val files = roots.map(discoverComponents(_).toList).reduce(_ ::: _)

    val JLineVersion = "2.12"
    val ScalaVersion = "2.11.5"
    val PatchedCompilerVersion = "2.11.5-20151022-113908-7fb0e653fd"

    val components = files.filter {
      case Component(DottyArtifact.Main, _, Some(Version(DottyVersions.DottyVersion)), _) => true
      case Component(DottyArtifact.JLine, _, Some(Version(JLineVersion)), _) => true
      case Component(DottyArtifact.Interfaces, _, _, _) => true
      case _ => false
    } ++
      files.filter {
        case Component(Artifact.ScalaLibrary, _, Some(Version(ScalaVersion)), _) => true
        case Component(Artifact.ScalaReflect, _, Some(Version(ScalaVersion)), _) => true
        case _ => false
      } ++
      files.filter {
        case Component(Artifact.ScalaCompiler, _, Some(Version(PatchedCompilerVersion)), _) => true
        case _ => false
      }

    val dottyInterfacesFile = CompileServerLauncher.dottyInterfacesJar
    val interfaceComponent = Component.discoverIn(Seq(dottyInterfacesFile))

    sdkDescriptor.from(components.toSeq ++ interfaceComponent).right.toSeq
  }
}
