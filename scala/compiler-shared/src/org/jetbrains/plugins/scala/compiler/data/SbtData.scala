package org.jetbrains.plugins.scala.compiler.data

import com.intellij.openapi.util.io.FileUtil

import java.io._
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile
import scala.io.Source
import scala.util.{Failure, Success, Using}

case class SbtData(sbtInterfaceJar: File,
                   compilerInterfaceJar: File,
                   compilerBridges: SbtData.CompilerBridges,
                   interfacesHome: File,
                   javaClassVersion: String) {
  private[data] def pluginJpsDirectory: Path = sbtInterfaceJar.getParentFile.toPath
}

object SbtData {

  case class Jars(sbtInterfaceJar: File, compilerInterfaceJar: File, compilerBridges: SbtData.CompilerBridges)

  object Jars {
    def fromPluginJpsDirectory(pluginJpsDir: Path): Jars = Jars(
      sbtInterfaceJar = pluginJpsDir.resolve("sbt-interface.jar").toFile,
      compilerInterfaceJar = pluginJpsDir.resolve("compiler-interface.jar").toFile,
      compilerBridges = CompilerBridges(
        scala = ScalaSourceJars(
          _2_10 = pluginJpsDir.resolve("compiler-interface-sources_2.10.jar").toFile,
          _2_11 = pluginJpsDir.resolve("compiler-interface-sources_2.11.jar").toFile,
          _2_13 = pluginJpsDir.resolve("compiler-interface-sources_2.13.jar").toFile
        ),
        scala3 = Scala3Jars(
          _3_0 = pluginJpsDir.resolve("scala3-sbt-bridge_3.0.jar").toFile,
          _3_1 = pluginJpsDir.resolve("scala3-sbt-bridge_3.1.jar").toFile,
          _3_2 = pluginJpsDir.resolve("scala3-sbt-bridge_3.2.jar").toFile,
          _3_3 = pluginJpsDir.resolve("scala3-sbt-bridge_3.3.jar").toFile
        )
      )
    )
  }

  case class CompilerBridges(scala: ScalaSourceJars, scala3: Scala3Jars)

  /**
   * Contains sources of the scala compiler bridges.
   * We must compile sources to use bridges.
   */
  case class ScalaSourceJars(_2_10: File, _2_11: File, _2_13: File)

  /**
   * Contains already compiled dotty/scala3 compiler bridges.
   */
  case class Scala3Jars(_3_0: File, _3_1: File, _3_2: File, _3_3: File)

  val compilerInterfacesKey = "scala.compiler.interfaces.dir"

  private def compilerInterfacesDir(systemRootDir: Path): File = {
    def defaultDir =
      systemRootDir.resolve("scala-compiler-interfaces").toFile

    val customPath = Option(System.getProperty(compilerInterfacesKey))
    customPath.map(new File(_)).getOrElse(defaultDir)
  }

  def from(pluginJpsRoot: File, javaClassVersion: String, systemRootDir: Path): Either[String, SbtData] =
    for {
      sbtHome <- Either.cond(pluginJpsRoot.exists, pluginJpsRoot, "Scala plugin jps directory does not exist: " + pluginJpsRoot)
      Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) = Jars.fromPluginJpsDirectory(sbtHome.toPath)
      sbtVersion <- readSbtVersionFrom(sbtInterfaceJar)
    } yield {
      val checksum = encodeHex(md5(compilerBridges.scala._2_10))
      val interfacesHome = new File(compilerInterfacesDir(systemRootDir), sbtVersion + "-idea-" + checksum)
      SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)
    }

  private def readSbtVersionFrom(sbtInterfaceJar: File): Either[String, String] =
    Using(new JarFile(sbtInterfaceJar)) {
      _.getManifest.getMainAttributes.getValue("Implementation-Version")
    } match {
      case Success(version) => Right(version)
      case Failure(t) => Left(s"Unable to read sbt version from JVM classpath:\n$t")
    }

  private def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      Using.resource(Source.fromFile(file, "UTF-8")) { source =>
        val text = source.mkString.replace("\r", "")
        md.digest(text.getBytes("UTF8"))
      }
    } else {
      Using.resource(new FileInputStream(file)) { input =>
        md.digest(FileUtil.loadBytes(input))
      }
    }
  }

  private val HexChars = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  private def encodeHex(bytes: Array[Byte]): String = {
    val out = new StringBuilder(bytes.length * 2)
    var i = 0
    while (i < bytes.length) {
      val b = bytes(i)
      out.append(HexChars((b >> 4) & 0xF))
      out.append(HexChars(b & 0xF))
      i += 1
    }
    out.toString()
  }
}
