package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector.ExtraCompilerPathResolveFailure
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector.ExtraCompilerPathResolveFailure._
import org.jetbrains.plugins.scala.project.template._

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}
import scala.util.Using


private[repository] object SystemDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = SystemSdkChoice(descriptor)
  override def friendlyName: String = ScalaBundle.message("system.wide.scala")

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private val scalaChildDirs = Set("bin", "lib")

  private val scala2LibChildFiles  = Set(
    "scala-compiler.jar",
    "scala-library.jar"
  )

  /**
   * NOTE: since Scala 3, compiler & library artifact jars include version suffix<br>
   * Examples:
   *  - scala3-compiler_3-3.0.1.jar
   *  - scala3-library_3-3.0.1-RC2.jar
   *  - scala3-library_3-3.0.1-qwe-123.jar
   */
  private def scala3LibChildFiles(version: String): Set[String] = Set(
    raw"scala3-library_3-$version.jar",
    raw"scala3-compiler_3-$version.jar"
  )
  private val VersionPropertyPrefix = "version:="

  private def containsRequiredScalaLibraryJars(scalaDir: Path): Boolean =
    containsRequiredScala2LibraryJars(scalaDir) ||
      containsRequiredScala3LibraryJars(scalaDir)

  private def containsRequiredScala2LibraryJars(scalaDir: Path): Boolean = {
    val libDir = scalaDir / "lib"
    scala2LibChildFiles.forall(libDir.childExists)
  }

  /**
   * Hierarchy of Scala3 zip:
   *  - bin
   *  - lib
   *  - VERSION (contains `version:=3.0.0`)
   */
  private def containsRequiredScala3LibraryJars(scalaDir: Path): Boolean = {
    val scala3Version = readVersionProperty(scalaDir) match {
      case Some(value) =>  value
      case None =>
        return false
    }

    val libDir = scalaDir / "lib"
    val expectedLibFiles = scala3LibChildFiles(scala3Version)
    expectedLibFiles.forall(libDir.childExists)
  }

  private def readVersionProperty(scalaDir: Path): Option[String] = {
    val versionFile = scalaDir / "VERSION"
    if (!versionFile.exists)
      None
    else
      Using.resource(scala.io.Source.fromFile(new File(versionFile.toUri))) { source =>
        val line = source.getLines().find(_.startsWith(VersionPropertyPrefix))
        line.map(_.stripPrefix(VersionPropertyPrefix).trim)
      }
  }

  private def rootsFromPrograms: Seq[Path] =
    if (SystemInfo.isWindows)
      (env("ProgramFiles").toSeq ++ env("ProgramFiles(x86)").toSeq).map(Paths.get(_))
    else if (SystemInfo.isMac)
      Seq("/opt/").map(Paths.get(_))
    else if (SystemInfo.isLinux)
      Seq("/usr/share/java/", "/usr/share/").map(Paths.get(_))
    else
      Seq.empty

  private def rootsFromPath: Seq[Path] = env("PATH").flatMap { path =>
    path.split(java.io.File.pathSeparator)
      .find(_.toLowerCase.contains("scala"))
      .map(s => Paths.get(s).getParent.toOption.map(_.getParent)) // we should return *parent* dir for "scala" folder, not the "bin" one
  }.toSeq.flatten

  private def rootsFromEnv: Seq[Path] = env("SCALA_HOME").map(Paths.get(_)).toSeq

  private def getSystemRoots: Seq[Path] = (rootsFromPath ++ rootsFromEnv ++ rootsFromPrograms).filter(_.exists)

  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val streams = getSystemRoots.map { root =>
      root
        .children
        .filter { dir =>
          progress(dir.toString)
          dir.isDir                                                  &&
            dir.getFileName.toString.toLowerCase.startsWith("scala") &&
            scalaChildDirs.forall(dir.childExists)                   &&
            containsRequiredScalaLibraryJars(dir)
        }
        .map[JStream[Path]](collectJarFiles)
        .flatMap(JFunction.identity[JStream[Path]]())
    }

    streams.foldLeft(JStream.empty[Path]()){ case (a, b) => JStream.concat(a,b) }
  }

  override protected def resolveExtraRequiredJarsScala3(descriptor: ScalaSdkDescriptor)
                                                       (implicit indicator: ProgressIndicator): Either[Seq[ExtraCompilerPathResolveFailure], ScalaSdkDescriptor] = {
    // assuming that all libraries are located in the same `lib` folder
    val systemRoot = descriptor.compilerClasspath.headOption.map(_.getParentFile) match {
      case Some(root) => root
      case None =>
        return Right(descriptor)
    }

    val jarArtifacts = systemRoot.children.flatMap(JarArtifact.from)

    val localResolveResults: Seq[Either[ExtraCompilerPathResolveFailure, JarArtifact]] =
      Scala3ExtraCompilerClasspathArtifacts.map(resolve(_, jarArtifacts))

    val (errors, jars) = localResolveResults.partitionMap(identity)
    if (errors.nonEmpty)
      Left(errors)
    else {
      val scala2LibraryJar = jars.find(_.shortName == Scala2LibraryArtifactName).get
      Right(
        descriptor
          .withExtraCompilerClasspath(jars.map(_.file))
          .withExtraLibraryFiles(Seq(scala2LibraryJar.file))
      )
    }
  }

  private def resolve(expectedArtifactName: String, jarArtifacts: Seq[JarArtifact]): Either[ExtraCompilerPathResolveFailure, JarArtifact] = {
    val matchingJars = jarArtifacts.filter(_.shortName == expectedArtifactName)
    matchingJars match {
      case Seq(jar)     => Right(jar).filterOrElse(_.file.exists(), UnresolvedArtifact(expectedArtifactName))
      case Seq()        => Left(UnresolvedArtifact(expectedArtifactName))
      case multipleJars => Left(AmbiguousArtifactsResolved(multipleJars.map(_.fileName)))
    }
  }

  private case class JarArtifact(file: File, fileName: String, shortName: String)
  private object JarArtifact {
    private val JarExtensionWithVersionRegex = """-\d+\.\d+\.\d+\S*\.jar$""".r

    def from(file: File): Option[JarArtifact] = {
      val fileName = file.getName
      if (fileName.endsWith(".jar")) {
        // extra strip ".jar"  just in case if some library doesn't have version (it's not the case for 3.0.0 though)
        val shortName = JarExtensionWithVersionRegex.replaceFirstIn(fileName, "").stripSuffix(".jar")
        Some(JarArtifact(file, fileName, shortName))
      }
      else None
    }
  }

  private val Scala2LibraryArtifactName = "scala-library"

  // This is the compiler classpath for Scala 3.0.0:
  // mvn dependency:tree (for scala3-compiler_3-3.0.0) (add -Dverbose to see duplicates)
  // \- org.scala-lang:scala3-compiler_3:jar:3.0.0:compile
  //    +- org.scala-lang:scala3-interfaces:jar:3.0.0:compile
  //    +- org.scala-lang:scala3-library_3:jar:3.0.0:compile
  //       \- org.scala-lang:scala-library:jar:2.13.5:compile
  //    +- org.scala-lang:tasty-core_3:jar:3.0.0:compile
  //    +- org.scala-lang.modules:scala-asm:jar:9.1.0-scala-1:compile
  //
  // === Compiler Interface ===
  //    +- org.scala-sbt:compiler-interface:jar:1.3.5:compile
  //       +- com.google.protobuf:protobuf-java:jar:3.7.0:compile
  //       \- org.scala-sbt:util-interface:jar:1.3.0:compile
  //
  // === JLine ===
  //    +- org.jline:jline-reader:jar:3.19.0:compile
  //    +- org.jline:jline-terminal:jar:3.19.0:compile
  //    \- org.jline:jline-terminal-jna:jar:3.19.0:compile
  //       +- net.java.dev.jna:jna:jar:5.3.1:compile
  //
  // NOTE:
  // We can't just include all jars from `lib` folder. We need an explicit list of compiler classpath dependencies
  // because `lib` folder contains a lot of other jar files, not required for the compiler.
  // For example:
  //  - scala3-tasty-inspector_3-3.0.0
  //  - scaladoc_3-3.0.0.jar with all it's transitive dependencies (flexmark-*.jar, jackson-*.jar, etc...)
  private val Scala3ExtraCompilerClasspathArtifacts = Seq(
    Scala2LibraryArtifactName,
    "scala-asm",
    "compiler-interface",
    "util-interface",
    "protobuf-java",
    "jline-reader",
    "jline-terminal",
    "jline-terminal-jna",
    "jna",
  )
}