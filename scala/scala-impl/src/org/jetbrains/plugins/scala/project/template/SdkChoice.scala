package org.jetbrains.plugins.scala
package project
package template

import java.io.File

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}

/**
 * @author Pavel Fatin
 */
sealed abstract class SdkChoice(val sdk: ScalaSdkDescriptor,
                                val source: String)

object SdkChoice {

  def findSdks(contextDirectory: VirtualFile): Seq[SdkChoice] =
    ProjectSdkChoice.skd(contextDirectory).toSeq ++
      SystemSdkChoice.sdks ++
      IvySdkChoice.sdks ++
      MavenSdkChoice.sdks

  private case class ProjectSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Project")

  private object ProjectSdkChoice {

    def skd(directory: VirtualFile): Option[ProjectSdkChoice] =
      for {
        virtualFile <- Option(directory)

        root = new File(VfsUtilCore.virtualToIoFile(virtualFile), "lib")
        sdk <- sdkIn(root)
      } yield ProjectSdkChoice(sdk)
  }

  private case class SystemSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "System")

  private object SystemSdkChoice {

    def sdks: Seq[SystemSdkChoice] =
      systemScalaRoots.flatMap { pathname =>
        sdkIn(new File(pathname))
      }.sorted.map(SystemSdkChoice(_))

    private def systemScalaRoots: Seq[String] = {
      val fromApps = systemAppRoots.filter(exists).flatMap(findScalaDirectoriesIn)

      val fromHome = env("SCALA_HOME")

      val fromCommandPath = env("PATH").flatMap(findScalaInCommandPath)

      (fromApps ++ fromHome ++ fromCommandPath).distinct.filter(exists)
    }

    private def systemAppRoots: Seq[String] = if (SystemInfo.isWindows) {
      env("ProgramFiles").toSeq ++ env("ProgramFiles(x86)").toSeq
    } else if (SystemInfo.isMac) {
      Seq("/opt/")
    } else if (SystemInfo.isLinux) {
      Seq("/usr/share/java/")
    } else {
      Seq.empty
    }

    private def findScalaDirectoriesIn(directory: String): Seq[String] = {
      val subdirectories = new File(directory).listFiles.toSeq
      subdirectories.filter(_.getName.toLowerCase.startsWith("scala")).map(_.getPath)
    }

    private def findScalaInCommandPath(path: String): Option[String] =
      path.split(File.pathSeparator)
        .find(_.toLowerCase.contains("scala"))
        .map(_.replaceFirst("""[/\\]?bin[/\\]?$""", ""))
  }

  private case class IvySdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Ivy")

  private object IvySdkChoice {

    def sdks: Seq[IvySdkChoice] = {
      val root = new File(userHome) / ".ivy2"

      val scalaFiles = (root / "cache" / "org.scala-lang").allFiles

      //    val dottyFiles = scalaFiles ++
      //      (root / "cache" / "me.d-d" / "scala-compiler").allFiles ++
      //      (root / "cache" / "ch.epfl.lamp").allFiles

      scalaSdksIn(scalaFiles).sorted.map(IvySdkChoice(_)) // ++ dottySdksIn(dottyFiles)
    }
  }

  private case class MavenSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Maven")

  private object MavenSdkChoice {

    def sdks: Seq[MavenSdkChoice] = {
      val root = new File(userHome) / ".m2"

      val scalaFiles = (root / "repository" / "org" / "scala-lang").allFiles

      scalaSdksIn(scalaFiles).sorted.map(MavenSdkChoice(_))
    }
  }

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private def exists(path: String): Boolean = new File(path).exists

  private def userHome = System.getProperty("user.home")

  private def sdkIn(root: File) =
    ScalaSdkDescriptor.from {
      Component.discoverIn(root.allFiles)
    }.toOption

  private def scalaSdksIn(files: Seq[File]): Seq[ScalaSdkDescriptor] =
    Component.discoverIn(files)
      .groupBy(_.version)
      .mapValues(ScalaSdkDescriptor.from)
      .toSeq
      .collect {
        case (Some(_), Right(sdk)) => sdk
      }

  //  private def dottySdksIn(files: Seq[File]): Seq[ScalaSdkDescriptor] = {
  //    val components = Component.discoverIn(files, Artifact.DottyArtifacts)
  //
  //    val patchedCompilers = components.filter {
  //      case Component(ScalaCompiler, Kind.Binaries, Some(_), file) if file.getAbsolutePath.contains("me.d-d") => true
  //      case _ => false
  //    }
  //
  //    if (patchedCompilers.isEmpty) Seq.empty
  //    else {
  //      val compilerComponent = patchedCompilers.maxBy(_.version.get)
  //
  //      val dottyComponents = components.filter {
  //        case Component(DottyCompiler | DottyLibrary, _, Some(_), _) => true
  //        case _ => false
  //      }
  //
  //      val otherComponents = components.filter {
  //        case Component(ScalaLibrary | ScalaReflect, _, Some(Version("2.11.5")), _) => true
  //        case _ => false
  //      }
  //
  //      dottyComponents.groupBy(_.version).values
  //        .map(components => ScalaSdkDescriptor.from(components ++ otherComponents :+ compilerComponent))
  //        .flatMap(_.right.toSeq)
  //        .toSeq
  //    }
  //  }

}