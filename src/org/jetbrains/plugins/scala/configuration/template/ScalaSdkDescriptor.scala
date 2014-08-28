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
    val reflectRequired = languageLevelFor(files).exists(_.isSinceScala2_10)

    val requiredJars = if (reflectRequired) Seq(Library, Compiler, Reflect) else Seq(Library, Compiler)
    
    val requiredBinaries = requiredJars.map(jar => (jar, files.find(jar.isBinary))).toMap

    val missingBinaries = requiredBinaries.collect {
      case (descriptor, None) => descriptor.title
    }

    if (missingBinaries.isEmpty) {
      val optionalJars = Seq(XML, Actors, Combinators, Swing)

      val optionalBinaries = optionalJars.map(jar => files.find(jar.isBinary)).flatten
      val docs = (Library +: optionalJars).map(jar => files.find(jar.isDocs)).flatten

      val libraryBinary = requiredBinaries(Library).get
      val compilerBinary = requiredBinaries(Compiler).get
      val reflectBinary = requiredBinaries.get(Reflect).flatten

      val compilerBinaries = Seq(libraryBinary, compilerBinary) ++ reflectBinary.toSeq
      val libraryBinaries = libraryBinary +: optionalBinaries

      JarFile.Library.versionOf(virtualToIoFile(libraryBinary)) match {
        case Some(libraryVersion) =>
          val compilerVersion = JarFile.Compiler.versionOf(virtualToIoFile(compilerBinary))
          val reflectVersion = reflectBinary.flatMap(it => JarFile.Reflect.versionOf(virtualToIoFile(it)))

          val otherVersions = Seq(compilerVersion, reflectVersion).flatten

          if (otherVersions.forall(_ == libraryVersion)) {
            val descriptor = ScalaSdkDescriptor(libraryVersion, compilerBinaries, libraryBinaries, Seq.empty, docs)
            Right(descriptor)
          } else {
            Left("Different versions of the core Scala JARs")
          }
        case None => Left("Cannot read Scala library version")
      }
    } else {
      Left("Not found: " + missingBinaries.mkString(", "))
    }
  }

  private def languageLevelFor(files: Seq[VirtualFile]): Option[ScalaLanguageLevel] = {
    files.find(Library.isBinary)
            .map(virtualToIoFile)
            .flatMap(JarFile.Library.versionOf)
            .flatMap(version => Option(ScalaLanguageLevel.from(version, false)))
  }
}
