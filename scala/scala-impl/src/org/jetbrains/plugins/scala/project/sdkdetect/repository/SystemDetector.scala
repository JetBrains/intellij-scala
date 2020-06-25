package org.jetbrains.plugins.scala.project.sdkdetect.repository

import java.nio.file.{Path, Paths}
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template._


private[repository] object SystemDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = SystemSdkChoice(descriptor)
  override def friendlyName: String = "System-wide Scala"

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private val scalaChildDirs = Set("bin", "lib")
  private val libChildFiles  = Set("scala-compiler.jar", "scala-library.jar")

  private def rootsFromPrograms: Seq[Path] =
    if (SystemInfo.isWindows) {
      (env("ProgramFiles").toSeq ++ env("ProgramFiles(x86)").toSeq).map(Paths.get(_))
    } else if (SystemInfo.isMac) {
      Seq("/opt/").map(Paths.get(_))
    } else if (SystemInfo.isLinux) {
      Seq("/usr/share/java/", "/usr/share/").map(Paths.get(_))
    } else { Seq.empty }

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
            libChildFiles.forall((dir / "lib").childExists)
        }
        .map[JStream[Path]](collectJarFiles)
        .flatMap(JFunction.identity[JStream[Path]]())
    }

    streams.foldLeft(JStream.empty[Path]()){ case (a, b) => JStream.concat(a,b) }
  }
}