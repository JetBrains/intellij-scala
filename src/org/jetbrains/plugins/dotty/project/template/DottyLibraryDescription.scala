package org.jetbrains.plugins.dotty.project.template

import java.io.File
import java.util
import javax.swing.JComponent

import org.jetbrains.plugins.dotty.project.{DottyLibraryKind, DottyVersions}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.template._

/**
  * @author adkozlov
  */
object DottyLibraryDescription extends ScalaLibraryDescription {
  override protected val LibraryKind = DottyLibraryKind

  override protected val SdkDescriptor = DottySdkDescriptor

  override protected def ivySdks = sdksIn(Seq(IvyRepository / "me.d-d", IvyRepository / "jline", IvyScalaRoot))

  override protected def mavenSdks = sdksIn(Seq(MavenRepository / "me" / "d-d",
    MavenRepository / "jline" / "jline", MavenScalaRoot))

  override def dialog(parentComponent: JComponent, provider: () => util.List[SdkChoice]) = {
    new DottySdkSelectionDialog(parentComponent, provider)
  }

  private def sdksIn(roots: Seq[File]): Seq[SdkDescriptor] = {
    val files = roots.map(discoverComponents(_).toList).reduce(_ ::: _)

    val components = files.filter {
      case Component(Artifact.DottyCompiler, _, Some(Version(DottyVersions.DottyVersion)), _) => true
      case Component(Artifact.DottyCompiler, _, Some(Version("2.12")), _) => true
      case _ => false
    } ++
      files.filter {
        case Component(Artifact.ScalaLibrary, _, Some(Version("2.11.5")), _) => true
        case Component(Artifact.ScalaReflect, _, Some(Version("2.11.5")), _) => true
        case _ => false
      } ++
      files.filter {
        case Component(Artifact.ScalaCompiler, _, Some(Version("2.11.5-20151022-113908-7fb0e653fd")), _) => true
        case _ => false
      }

    SdkDescriptor.from(components.toSeq).right.toSeq
  }
}
