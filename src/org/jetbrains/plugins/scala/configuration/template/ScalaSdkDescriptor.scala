package org.jetbrains.plugins.scala
package configuration.template

import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.configuration.template.JarPattern._
import org.jetbrains.plugins.scala.configuration.{ScalaLanguageLevel, ScalaLibraryProperties, ScalaLibraryType}

/**
 * @author Pavel Fatin
 */
case class ScalaSdkDescriptor(version: String,
                              compilerFiles: Seq[VirtualFile],
                              libraryFiles: Seq[VirtualFile],
                              sourceFiles: Seq[VirtualFile],
                              docFiles: Seq[VirtualFile]) {

  def createNewLibraryConfiguration() = {
    val properties = new ScalaLibraryProperties()

    properties.languageLevel = ScalaLanguageLevel.from(version, true)
    properties.compilerClasspath = compilerFiles.map(virtualToIoFile)

    val name = "scala-sdk-" + version

    new NewLibraryConfiguration(name, ScalaLibraryType.instance, properties) {
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
    val requiredBinaries = Seq(Library, Compiler, Reflect).map(jar => (jar, files.find(jar.isBinary))).toMap

    val missingBinaries = requiredBinaries.collect {
      case (descriptor, None) => descriptor.title
    }

    if (missingBinaries.isEmpty) {
      val optionalJars = Seq(XML, Actors, Combinators, Swing)

      val optionalBinaries = optionalJars.map(jar => files.find(jar.isBinary)).flatten
      val docs = (Library +: optionalJars).map(jar => files.find(jar.isDocs)).flatten

      val libraryBinary = requiredBinaries(Library).get
      val compilerBinary = requiredBinaries(Compiler).get
      val reflectBinary = requiredBinaries(Reflect).get

      val compilerBinaries = Seq(libraryBinary, compilerBinary, reflectBinary)
      val libraryBinaries = libraryBinary +: optionalBinaries

      val libraryVersion = JarFile.Library.versionOf(virtualToIoFile(libraryBinary))
      val compilerVersion = JarFile.Compiler.versionOf(virtualToIoFile(compilerBinary))
      val reflectVersion = JarFile.Reflect.versionOf(virtualToIoFile(reflectBinary))

      if (libraryVersion.isDefined && libraryVersion == compilerVersion && reflectVersion == compilerVersion) {
        val descriptor = ScalaSdkDescriptor(libraryVersion.get, compilerBinaries, libraryBinaries, Seq.empty, docs)
        Right(descriptor)
      } else {
        Left("Different versions of the core JARs")
      }
    } else {
      Left("Not found: " + missingBinaries.mkString(", "))
    }
  }
}
