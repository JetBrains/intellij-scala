package org.jetbrains.plugins.scala
package project.template

import java.io.File

import com.intellij.openapi.roots.libraries.{LibraryType, NewLibraryConfiguration}
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaLibrary

/**
 * @author Pavel Fatin
 */
case class ScalaSdkDescriptor(version: Option[Version],
                              compilerFiles: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) extends SdkDescriptor {
  override protected val languageName = "scala"

  override protected val libraryType: LibraryType[ScalaLibraryProperties] = ScalaLibraryType.instance

  override protected val libraryName: String = s"$languageName-sdk-" + version.map(_.number).getOrElse("Unknown")

  override protected def libraryProperties: ScalaLibraryProperties = {
    val properties = new ScalaLibraryProperties()

    properties.languageLevel = version.flatMap(ScalaLanguageLevel.from).getOrElse(ScalaLanguageLevel.Default)
    properties.compilerClasspath = compilerFiles
    properties
  }
}

trait SdkDescriptor {
  val version: Option[Version]
  val compilerFiles: Seq[File]
  val libraryFiles: Seq[File]
  val sourceFiles: Seq[File]
  val docFiles: Seq[File]

  protected val languageName: String

  protected val libraryType: LibraryType[ScalaLibraryProperties]

  protected val libraryName: String

  protected def libraryProperties: ScalaLibraryProperties

  def createNewLibraryConfiguration() = {
    new NewLibraryConfiguration(libraryName, libraryType, libraryProperties) {
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

object ScalaSdkDescriptor extends SdkDescriptorCompanion {
  override protected val requiredBinaries: Set[Artifact] = Set()

  override protected val libraryArtifacts = Artifact.values - Artifact.ScalaCompiler

  override protected def createSdkDescriptor(version: Option[Version],
                                             compilerFiles: Seq[File],
                                             libraryFiles: Seq[File],
                                             sourceFiles: Seq[File],
                                             docFiles: Seq[File]) = {
    ScalaSdkDescriptor(version, compilerFiles, libraryFiles, sourceFiles, docFiles)
  }
}

trait SdkDescriptorCompanion {
  protected val requiredBinaries: Set[Artifact]

  protected val libraryArtifacts: Set[Artifact]

  protected def createSdkDescriptor(version: Option[Version],
                                    compilerFiles: Seq[File],
                                    libraryFiles: Seq[File],
                                    sourceFiles: Seq[File],
                                    docFiles: Seq[File]): SdkDescriptor

  def from(components: Seq[Component]): Either[String, SdkDescriptor] = {
    val (binaryComponents, sourceComponents, docComponents) = {
      val componentsByKind = components.groupBy(_.kind)

      (componentsByKind.getOrElse(Kind.Binaries, Seq.empty),
              componentsByKind.getOrElse(Kind.Sources, Seq.empty),
              componentsByKind.getOrElse(Kind.Docs, Seq.empty))
    }

    val reflectRequired = binaryComponents.exists {
      _.version.exists {
        _.toLanguageLevel.exists(_ >= Scala_2_10)
      }
    }

    val requiredBinaryArtifacts = Set(Artifact.ScalaLibrary, Artifact.ScalaCompiler) ++ requiredBinaries ++ (
      if (reflectRequired) Set(Artifact.ScalaReflect)
      else Set())

    val existingBinaryArtifacts = binaryComponents.map(_.artifact).toSet

    val missingBinaryArtifacts = requiredBinaryArtifacts -- existingBinaryArtifacts

    if (missingBinaryArtifacts.isEmpty) {
      val compilerBinaries = binaryComponents.filter(it => requiredBinaryArtifacts.contains(it.artifact))

      val libraryBinaries = binaryComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val librarySources = sourceComponents.filter(it => libraryArtifacts.contains(it.artifact))
      val libraryDocs = docComponents.filter(it => libraryArtifacts.contains(it.artifact))

      val libraryVersion = binaryComponents.find(_.artifact == ScalaLibrary).flatMap(_.version)

      val descriptor = createSdkDescriptor(
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
