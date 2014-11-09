package org.jetbrains.plugins.scala
package project.template

import java.io.File

import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaLibrary
import org.jetbrains.plugins.scala.project._

/**
 * @author Pavel Fatin
 */
case class ScalaSdkDescriptor(version: Option[Version],
                              compilerFiles: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) {

  def createNewLibraryConfiguration() = {
    val properties = new ScalaLibraryProperties()

    properties.languageLevel = version.flatMap(ScalaLanguageLevel.from).getOrElse(ScalaLanguageLevel.Default)
    properties.compilerClasspath = compilerFiles

    val name = "scala-sdk-" + version.map(_.number).getOrElse("Unknown")

    new NewLibraryConfiguration(name, ScalaLibraryType.instance, properties) {
      override def addRoots(editor: LibraryEditor): Unit = {
        libraryFiles.map(_.toLibraryRootURL).foreach(editor.addRoot(_, OrderRootType.CLASSES))
        sourceFiles.map(_.toLibraryRootURL).foreach(editor.addRoot(_, OrderRootType.SOURCES))
        docFiles.map(_.toLibraryRootURL).foreach(editor.addRoot(_, JavadocOrderRootType.getInstance))

        if (sourceFiles.isEmpty && docFiles.isEmpty) {
          editor.addRoot(ScalaSdk.documentationUrlFor(version), JavadocOrderRootType.getInstance)
        }
      }
    }
  }
}

object ScalaSdkDescriptor {
  def from(components: Seq[Component]): Either[String, ScalaSdkDescriptor] = {
    val (binaryComponents, sourceComponents, docComponents) = {
      val componentsByKind = components.groupBy(_.kind)

      (componentsByKind.getOrElse(Kind.Binaries, Seq.empty),
              componentsByKind.getOrElse(Kind.Sources, Seq.empty),
              componentsByKind.getOrElse(Kind.Docs, Seq.empty))
    }

    val reflectRequired = binaryComponents.exists { component =>
      component.version.exists { version =>
        ScalaLanguageLevel.from(version).exists(_ >= Scala_2_10)
      }
    }

    val requiredBinaryArtifacts: Set[Artifact] =
      if (reflectRequired) Set(Artifact.ScalaLibrary, Artifact.ScalaCompiler, Artifact.ScalaReflect)
      else Set(Artifact.ScalaLibrary, Artifact.ScalaCompiler)

    val existingBinaryArtifacts = binaryComponents.map(_.artifact).toSet

    val missingBinaryArtifacts = requiredBinaryArtifacts -- existingBinaryArtifacts

    if (missingBinaryArtifacts.isEmpty) {
      val compilerBinaries = binaryComponents.filter(it => requiredBinaryArtifacts.contains(it.artifact))

      val libraryArtifacts = Artifact.values - Artifact.ScalaCompiler

      val libraryBinaries = binaryComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val librarySources = sourceComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val libraryDocs = docComponents.filter(it => libraryArtifacts.contains(it.artifact))

      val libraryVersion = binaryComponents.find(_.artifact == ScalaLibrary).flatMap(_.version)

      val descriptor = ScalaSdkDescriptor(
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
}
