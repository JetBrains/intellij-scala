package org.jetbrains.plugins.scala
package configuration.template

import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.{JarFileSystem, VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.configuration.template.JarDescriptor._
import org.jetbrains.plugins.scala.configuration.{ScalaLanguageLevel, ScalaLibraryProperties, ScalaLibraryType}

/**
 * @author Pavel Fatin
 */
case class ScalaSdkDescriptor(compilerFiles: Seq[VirtualFile],
                              libraryFiles: Seq[VirtualFile],
                              sourceFiles: Seq[VirtualFile],
                              docFiles: Seq[VirtualFile]) {

  def createNewLibraryConfiguration() = {
    val properties = new ScalaLibraryProperties()

    properties.languageLevel = ScalaLanguageLevel.getDefault
    properties.compilerClasspath = compilerFiles.map(VfsUtilCore.virtualToIoFile)

    new NewLibraryConfiguration("scala-sdk", ScalaLibraryType.instance, properties) {
      override def addRoots(editor: LibraryEditor): Unit = {
        libraryFiles.map(toJarFile).foreach(editor.addRoot(_, OrderRootType.CLASSES))
        sourceFiles.map(toJarFile).foreach(editor.addRoot(_, OrderRootType.SOURCES))
        docFiles.map(toJarFile).foreach(editor.addRoot(_, JavadocOrderRootType.getInstance))
      }
    }
  }
}

object ScalaSdkDescriptor {
  def from(files: Seq[VirtualFile]): Either[String, ScalaSdkDescriptor] = {
    val requiredBinaries = Seq(Library, Compiler, Reflection).map(jar => (jar, files.find(jar.isBinary))).toMap

    val missingBinaries = requiredBinaries.collect {
      case (descriptor, None) => descriptor.title
    }

    if (missingBinaries.isEmpty) {
      val optionalJars = Seq(XML, Actors, Combinators, Swing)

      val optionalBinaries = optionalJars.map(jar => files.find(jar.isBinary)).flatten
      val docs = (Library +: optionalJars).map(jar => files.find(jar.isDocs)).flatten

      val libraryBinary = requiredBinaries(Library).get
      val compilerBinary = requiredBinaries(Compiler).get
      val reflectionBinary = requiredBinaries(Reflection).get

      val compilerBinaries = Seq(libraryBinary, compilerBinary, reflectionBinary)
      val libraryBinaries = libraryBinary +: optionalBinaries

      val descriptor = ScalaSdkDescriptor(compilerBinaries, libraryBinaries, Seq.empty, docs)

      Right(descriptor)
    } else {
      Left("Not found: " + missingBinaries.mkString(", "))
    }
  }
}
