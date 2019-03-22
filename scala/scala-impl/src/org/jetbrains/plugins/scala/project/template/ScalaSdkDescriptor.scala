package org.jetbrains.plugins.scala
package project
package template

import java.io.File

import com.intellij.openapi.roots.JavadocOrderRootType.{getInstance => JAVA_DOCS}
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor

/**
 * @author Pavel Fatin
 */
case class ScalaSdkDescriptor(version: Option[Version],
                              compilerClasspath: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) {

  def createNewLibraryConfiguration(): NewLibraryConfiguration = {
    val suffix = version.map(_.presentation).getOrElse("Unknown")

    new NewLibraryConfiguration(
      "scala-sdk-" + suffix,
      ScalaLibraryType(),
      ScalaLibraryProperties.applyByVersion(version, compilerClasspath)
    ) {
      override def addRoots(editor: LibraryEditor): Unit = {
        def addRoot(file: File, rootType: OrderRootType): Unit =
          editor.addRoot(file.toLibraryRootURL, rootType)

        (libraryFiles ++ sourceFiles).foreach(addRoot(_, OrderRootType.CLASSES))
        docFiles.foreach(addRoot(_, JAVA_DOCS))

        if (sourceFiles.isEmpty && docFiles.isEmpty) {
          editor.addRoot(ScalaSdk.documentationUrlFor(version), JAVA_DOCS)
        }
      }
    }
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

        val descriptor = ScalaSdkDescriptor(
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