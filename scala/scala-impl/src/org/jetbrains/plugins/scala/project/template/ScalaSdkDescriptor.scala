package org.jetbrains.plugins.scala
package project.template

import java.io.File

import com.intellij.openapi.roots.JavadocOrderRootType.{getInstance => JAVA_DOCS}
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import org.jetbrains.plugins.scala.project.Platform._
import org.jetbrains.plugins.scala.project._

/**
  * @author Pavel Fatin
  */
case class ScalaSdkDescriptor(platform: Platform,
                              version: Option[Version],
                              compilerFiles: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) {

  def createNewLibraryConfiguration(): NewLibraryConfiguration = {
    val properties = new ScalaLibraryProperties()

    properties.platform = platform
    properties.languageLevel = platform match {
      case Scala => version.flatMap(_.toLanguageLevel).getOrElse(ScalaLanguageLevel.Default)
      case Dotty => ScalaLanguageLevel.Snapshot
    }
    properties.compilerClasspath = compilerFiles

    val name = platform match {
      case Scala => "scala-sdk-" + version.map(_.presentation).getOrElse("Unknown")
      case Dotty => "dotty-sdk"
    }

    new NewLibraryConfiguration(name, ScalaLibraryType.instance, properties) {
      override def addRoots(editor: LibraryEditor): Unit = {
        def addRoot(file: File, rootType: OrderRootType): Unit =
          editor.addRoot(file.toLibraryRootURL, rootType)

        (libraryFiles ++ sourceFiles).foreach(addRoot(_, CLASSES))
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
    val platform = if (components.map(_.artifact).contains(DottyCompiler)) Dotty else Scala

    val (binaryComponents, sourceComponents, docComponents) = {
      val componentsByKind = components.groupBy(_.kind)

      (componentsByKind.getOrElse(Binaries, Seq.empty),
        componentsByKind.getOrElse(Sources, Seq.empty),
        componentsByKind.getOrElse(Docs, Seq.empty))
    }

    val requiredBinaryArtifacts = binaryArtifactsFor(platform)

    val existingBinaryArtifacts = binaryComponents.map(_.artifact).toSet

    val missingBinaryArtifacts = requiredBinaryArtifacts -- existingBinaryArtifacts

    if (missingBinaryArtifacts.isEmpty) {
      val compilerBinaries = binaryComponents.filter(it => requiredBinaryArtifacts.contains(it.artifact))

      val libraryArtifacts = libraryArtifactsFor(platform)

      val libraryBinaries = binaryComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val librarySources = sourceComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val libraryDocs = docComponents.filter(it => libraryArtifacts.contains(it.artifact))

      val libraryVersion = platform match {
        case Dotty =>
          binaryComponents.find(_.artifact == DottyCompiler).flatMap(_.version)
        case _ =>
          binaryComponents.find(_.artifact == ScalaLibrary).flatMap(_.version)
      }

      val descriptor = ScalaSdkDescriptor(
        platform,
        libraryVersion,
        compilerBinaries.map(_.file),
        libraryBinaries.map(_.file),
        librarySources.map(_.file),
        libraryDocs.map(_.file))

      Right(descriptor)
    } else {
      Left("Not found: " + missingBinaryArtifacts.map(_.title).mkString(", "))
    }
  }

  private[this] def binaryArtifactsFor(platform: Platform): Set[Artifact] =
    Set(ScalaLibrary, ScalaCompiler, ScalaReflect) ++ (platform match {
      case Dotty => Set(DottyLibrary)
      case _ => Set.empty[Artifact]
    })

  private[this] def libraryArtifactsFor(platform: Platform): Set[Artifact] =
    (platform match {
      case Scala => ScalaArtifacts
      case Dotty => DottyArtifacts
    }) - ScalaCompiler
}