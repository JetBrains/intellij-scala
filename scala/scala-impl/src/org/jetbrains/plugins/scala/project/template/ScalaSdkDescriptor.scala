package org.jetbrains.plugins.scala
package project
package template

import java.io.File

import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor

/**
 * @author Pavel Fatin
 */
final class ScalaSdkDescriptor(private val version: Option[Version],
                               private val compilerClasspath: Seq[File],
                               private val libraryFiles: Seq[File],
                               private val sourceFiles: Seq[File],
                               private val docFiles: Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  def hasSources: Boolean = sourceFiles.nonEmpty

  def hasDocs: Boolean = docFiles.nonEmpty

  def versionText(default: String = "Unknown")
                 (presentation: Version => String = _.presentation): String =
    version.fold(default)(presentation)

  override def compare(that: ScalaSdkDescriptor): Int = that.version.compare(version)

  //noinspection TypeAnnotation
  def createNewLibraryConfiguration = new NewLibraryConfiguration(
    "scala-sdk-" + versionText()(),
    ScalaLibraryType(),
    ScalaLibraryProperties.applyByVersion(version, compilerClasspath)
  ) {
    override def addRoots(editor: LibraryEditor): Unit = {
      addRootsInner(libraryFiles ++ sourceFiles)(editor)
      addRootsInner(docFiles, JavadocOrderRootType.getInstance)(editor)

      if (sourceFiles.isEmpty && docFiles.isEmpty) editor.addRoot(
        s"https://www.scala-lang.org/api/${versionText("current")()}/",
        JavadocOrderRootType.getInstance
      )
    }

    private def addRootsInner(files: Seq[File],
                              rootType: OrderRootType = OrderRootType.CLASSES)
                             (implicit editor: LibraryEditor): Unit =
      for {
        file <- files
        url = file.toLibraryRootURL
      } editor.addRoot(url, rootType)
  }
}

object ScalaSdkDescriptor {

  import Artifact._
  import Kind._

  def from(components: Seq[Component]): Either[String, ScalaSdkDescriptor] = {
    val componentsByKind = components.groupBy(_.kind)
      .withDefault(Function.const(Seq.empty))

    def filesByKind(kind: Kind) =
      files(componentsByKind(kind))()

    val binaryComponents = componentsByKind(Binaries)

    requiredBinaryArtifacts -- binaryComponents.map(_.artifact) match {
      case missingBinaryArtifacts if missingBinaryArtifacts.nonEmpty =>
        Left("Not found: " + missingBinaryArtifacts.map(_.prefix + "*.jar").mkString(", "))
      case _ =>
        val libraryVersion = binaryComponents.collectFirst {
          case Component(ScalaLibrary, _, Some(version), _) => version
        }

        val descriptor = new ScalaSdkDescriptor(
          libraryVersion,
          files(binaryComponents)(requiredBinaryArtifacts),
          files(binaryComponents)(),
          filesByKind(Sources),
          filesByKind(Docs)
        )

        Right(descriptor)
    }
  }

  private[this] def requiredBinaryArtifacts = Set[Artifact](
    ScalaLibrary,
    ScalaCompiler,
    ScalaReflect
  )

  private[this] def files(components: Seq[Component])
                         (predicate: Artifact => Boolean = ScalaArtifacts - ScalaCompiler) =
    for {
      Component(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}