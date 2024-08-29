package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CompilerClasspathResolveFailure.{AmbiguousArtifactsResolved, CantReadClasspathFromManifest, UnresolvedArtifact}
import org.jetbrains.plugins.scala.project.template._
import org.jetbrains.plugins.scala.util.JarManifestUtils

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Using


private[project] object SystemDetector extends ScalaSdkDetectorBase {

  private val Log = Logger.getInstance(this.getClass)

  override def friendlyName: String = ScalaBundle.message("system.wide.scala")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = SystemSdkChoice(descriptor)

  private val BinFolder = "bin"
  private val LibFolder = "lib"
  private val scalaChildDirs = Set(BinFolder, LibFolder)

  // Jar files storing information about the class path since scala 3.5.0
  // NOTE: there is also "with_compiler.jar" but it seems we don't need it
  // From https://github.com/scala/scala3/issues/20413:
  // "there is also a scala3-tasty-inspector_3 jar to go alongside scala3-staging_3
  //   these are not normally on the classpath for scala/scalac,
  //   but are added to the classpath if the user passes the -with-compiler argument to the launcher.
  //   (Typically the user would need to add explicit libraryDependencies to use them in sbt/mill)"
  private val ScalaCompilerClasspathJarName = "scala.jar"
  private val ScaladocClasspathJarName = "scaladoc.jar"

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
    val libDir = scalaDir / LibFolder
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

    val libDir = scalaDir / LibFolder
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

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private def rootsFromEnv: Seq[Path] = env("SCALA_HOME").map(Paths.get(_)).toSeq

  private def getSystemRoots: Seq[Path] = (rootsFromPath ++ rootsFromEnv ++ rootsFromPrograms).filter(_.exists)

  override protected def collectSdkDescriptors(implicit indicator: ProgressIndicator): Seq[ScalaSdkDescriptor] = {
    val scalaSdkRoots: Seq[Path] = findPotentialScalaSdkRoots
    val jarStreams: Seq[(JStream[Path], Path)] = scalaSdkRoots.map { scalaSdkRoot =>
      (collectJarFiles(scalaSdkRoot), scalaSdkRoot)
    }

    val components: Seq[(Seq[ScalaSdkComponent], Path)] =
      jarStreams.map { case (jarStream, sdkRootPath) =>
        (componentsFromJarStream(jarStream), sdkRootPath)
      }

    val sdkDescriptorsOrErrors: Seq[(Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor], Path)] =
      components.map { case (components, sdkRootPath) =>
        (buildFromComponents(components, None, systemRoot = Some(sdkRootPath.toFile), indicator = indicator), sdkRootPath)
      }

    val sdkDescriptors: Seq[(ScalaSdkDescriptor, Path)] = sdkDescriptorsOrErrors.flatMap {
      case (Left(errors), path)             =>
        logScalaSdkSkippedInPath(path, errors.map(_.errorMessage))
        None
      case (Right(descriptor), sdkRootPath) =>
        Some((descriptor, sdkRootPath))
    }
    val versionsCount = sdkDescriptors.groupBy(_._1.version).view.mapValues(_.size).toMap
    sdkDescriptors.map { case (sdk, sdkRootPath) =>
      // show sdkRoot folder name as label only if there are several system SDKs with same version
      val label = if (versionsCount(sdk.version) > 1) Some(sdkRootPath.getFileName.toString) else None
      sdk.withLabel(label)
    }
  }

  private final def logScalaSdkSkippedInPath(sdkRootPath: Path, errors: Seq[String]): Unit = {
    Log.trace(
      s"Scala SDK Descriptor candidate is skipped" +
        s" (detector: ${this.getClass.getSimpleName}, sdkRootPath: $sdkRootPath)," +
        s" errors: ${errors.zipWithIndex.map(_.swap).mkString(", ")}"
    )
  }

  private def findPotentialScalaSdkRoots(implicit indicator: ProgressIndicator): Seq[Path] = {
    val systemRoots = getSystemRoots
    systemRoots.flatMap { root =>
      Using.resource(root.children) { childrenStream =>
        childrenStream
          .filter { path =>
            progress(path.toString)
            isScalaSdkFolder(path)
          }
          .iterator().asScala.toSeq
      }
    }
  }

  private def isScalaSdkFolder(path: Path): Boolean = {
    path.isDir &&
      path.getFileName.toString.toLowerCase.startsWith("scala") &&
      scalaChildDirs.forall(path.childExists) &&
      (containsRequiredScalaLibraryJars(path) || findCompilerClasspathJar(path.toFile).isDefined)
  }

  def buildSdkDescriptor(selectedFiles: Seq[VirtualFile]): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val files = selectedFiles.map(VfsUtilCore.virtualToIoFile)

    val systemRoot = files match {
      case Seq(f) if f.isDirectory => Some(f)
      case _ => None
    }

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)
    val components = ScalaSdkComponent.fromFiles(allFiles)
    buildFromComponents(components, None, systemRoot = systemRoot)
  }

  override protected def resolveExtraRequiredJarsScala3(descriptor: ScalaSdkDescriptor)
                                                       (implicit indicator: ProgressIndicator): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    // assuming that all libraries are located in the same `lib` folder
    val systemRoot = descriptor.systemRoot.orElse(guessSystemRootFromClasspath(descriptor)) match {
      case Some(root) => root
      case None =>
        return Right(descriptor)
    }

    val jarWithScalaClassPath = findCompilerClasspathJar(systemRoot).orNull
    if (jarWithScalaClassPath != null) //should be effectively true since 3.5.0
      resolveUsingJarMetaInfo(systemRoot, jarWithScalaClassPath, descriptor) //TODO: think about fallback mechanism
    else
      resolveUsingHardcodedJarNames(systemRoot, descriptor)
  }

  private def findCompilerClasspathJar(systemRoot: File): Option[File] =
    Option(systemRoot / LibFolder / ScalaCompilerClasspathJarName).filter(_.exists())

  private def guessSystemRootFromClasspath(descriptor: ScalaSdkDescriptor): Option[File] =
    descriptor.compilerClasspath.headOption //jar file
      .map(_.getParentFile) //lib dir
      .map(_.getParentFile) //system root

  /**
   * @note from scala 3.5.0, the scala distribution keeps information about the classpath in `*.jar/META-INF/MANIFEST.MF`
   */
  private def resolveUsingJarMetaInfo(
    systemRoot: File,
    jarWithScalaClassPath: File,
    descriptor: ScalaSdkDescriptor
  ): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val compilerClasspath = readClassPath(jarWithScalaClassPath)
    compilerClasspath.map { cp =>
      val jarWithScaladocClasspath = systemRoot / LibFolder / ScaladocClasspathJarName
      val scaladocClasspath = readClassPath(jarWithScaladocClasspath).getOrElse(Nil)
      val scaladocExtraClasspath = scaladocClasspath.filterNot(cp.contains)

      val compilerBridgeJar = descriptor.version.flatMap(findCompilerBridgeJarInSdkLocalMavenRepo(systemRoot, _))

      //NOTE: library classes are assumed to be detected already
      descriptor.copy(
        compilerClasspath = cp,
        scaladocExtraClasspath = scaladocExtraClasspath,
        compilerBridgeJar = compilerBridgeJar
      )
    }
  }

  private def readClassPath(jarFile: File): Either[Seq[CompilerClasspathResolveFailure], Seq[File]] = {
    val classPath = JarManifestUtils.readClassPath(jarFile).orNull
    if (classPath != null) {
      val missingFiles = classPath.filterNot(_.exists())
      if (missingFiles.isEmpty)
        Right(classPath)
      else
        Left(missingFiles.map(CompilerClasspathResolveFailure.FileNotFound.apply))
    }
    else {
      Left(Seq(CantReadClasspathFromManifest(jarFile)))
    }
  }

  // Since version 3.5.0, scala distribution keeps all the jars in the local maven2 directory, including the bridge jar.
  // Note that even if no jar is found, it will be later downloaded dynamically when registering SDK
  private def findCompilerBridgeJarInSdkLocalMavenRepo(systemRoot: File, version: String) : Option[File] = {
    val jarFileName = ScalaSdkUtils.compilerBridgeJarName(version)
    val jarFile = jarFileName.map(systemRoot / "maven2" / "org" / "scala-lang" / "scala3-sbt-bridge" / version / _)
    jarFile.filter(_.exists())
  }

  private def resolveUsingHardcodedJarNames(
    systemRoot: File,
    descriptor: ScalaSdkDescriptor
  ): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val jarArtifacts = (systemRoot / LibFolder).children.flatMap(JarArtifact.from)

    val localResolveResults: Seq[Either[CompilerClasspathResolveFailure, JarArtifact]] =
      Scala3ExtraCompilerClasspathArtifacts.map(resolve(_, jarArtifacts)) ++
        Scala3ExtraCompilerClasspathOptionalArtifacts.map(resolve(_, jarArtifacts)).filter(_.isRight)

    val (errors, jars) = localResolveResults.partitionMap(identity)
    if (errors.nonEmpty)
      Left(errors)
    else {
      Right(
        descriptor.withExtraCompilerClasspath(jars.map(_.file))
      )
    }
  }

  private def resolve(expectedArtifactName: String, jarArtifacts: Seq[JarArtifact]): Either[CompilerClasspathResolveFailure, JarArtifact] = {
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
        // extra strip ".jar" just in case if some library doesn't have version (it's not the case for 3.0.0 though)
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
    "jline-reader",
    "jline-terminal",
    "jline-terminal-jna",
    "jna",
  )
  //This jar is not required in scala 3.3.x or 3.4.x
  private val Scala3ExtraCompilerClasspathOptionalArtifacts = Seq(
    "protobuf-java",
  )
}