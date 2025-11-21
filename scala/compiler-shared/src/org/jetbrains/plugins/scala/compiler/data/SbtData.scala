package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.plugins.scala.util.JarManifestUtils

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.io.Source
import scala.util.Using
import scala.util.control.NonFatal

case class SbtData(sbtInterfaceJar: Path,
                   compilerInterfaceJar: Path,
                   compilerBridges: SbtData.CompilerBridges,
                   interfacesHome: Path,
                   javaClassVersion: String) {
  private[data] def pluginJpsDirectory: Path = sbtInterfaceJar.getParent
}

object SbtData {

  def serialize(data: SbtData): Seq[String] = {
    import serialization.SerializationUtils.pathToString
    val SbtData(_, _, _, interfacesHome, javaClassVersion) = data

    Seq(
      pathToString(data.pluginJpsDirectory),
      pathToString(interfacesHome),
      javaClassVersion
    )
  }

  import Extractors.StringToPath

  def deserialize(strings: Seq[String]): Either[String, (SbtData, Seq[String])] = strings match {
    case StringToPath(pluginJpsDirectory) +:
      StringToPath(interfacesHome) +:
      javaClassVersion +:
      tail =>

      val Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) =
        Jars.fromPluginJpsDirectory(pluginJpsDirectory)
      Right(SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion) -> tail)

    case args => Left(s"The arguments don't match the expected shape of CompilerData: ${args.mkString("[", ",", "]")}")
  }

  case class Jars(sbtInterfaceJar: Path, compilerInterfaceJar: Path, compilerBridges: SbtData.CompilerBridges)

  object Jars {
    def fromPluginJpsDirectory(pluginJpsDir: Path): Jars = Jars(
      sbtInterfaceJar = pluginJpsDir.resolve("sbt-interface.jar"),
      compilerInterfaceJar = pluginJpsDir.resolve("compiler-interface.jar"),
      compilerBridges = CompilerBridges(
        scala = ScalaSourceJars(
          _2_10 = pluginJpsDir.resolve("compiler-bridge-sources_2.10.jar"),
          _2_11 = pluginJpsDir.resolve("compiler-bridge-sources_2.11.jar"),
          _2_13 = pluginJpsDir.resolve("compiler-bridge-sources_2.13.jar"),
        ),
        scala3 = Scala3Jars(
          _3_0 = pluginJpsDir.resolve("scala3-sbt-bridge_3.0.jar"),
          _3_1 = pluginJpsDir.resolve("scala3-sbt-bridge_3.1.jar"),
          _3_2 = pluginJpsDir.resolve("scala3-sbt-bridge_3.2.jar"),
          _3_3_1 = pluginJpsDir.resolve("scala3-sbt-bridge_3.3.1.jar"),
          _3_3 = pluginJpsDir.resolve("scala3-sbt-bridge_3.3.jar"),
          _3_4 = pluginJpsDir.resolve("scala3-sbt-bridge_3.4.jar")
        )
      )
    )
  }

  case class CompilerBridges(scala: ScalaSourceJars, scala3: Scala3Jars)

  /**
   * Contains sources of the scala compiler bridges.
   * We must compile sources to use bridges.
   */
  case class ScalaSourceJars(_2_10: Path, _2_11: Path, _2_13: Path)

  /**
   * Contains already compiled dotty/scala3 compiler bridges.
   */
  case class Scala3Jars(_3_0: Path, _3_1: Path, _3_2: Path, _3_3_1: Path, _3_3: Path, _3_4: Path)

  val compilerInterfacesKey = "scala.compiler.interfaces.dir"

  private def compilerInterfacesDir(systemRootDir: Path): Path = {
    def defaultDir =
      systemRootDir.resolve("scala-compiler-interfaces")

    val customPath = Option(System.getProperty(compilerInterfacesKey))
    customPath.map(Paths.get(_)).getOrElse(defaultDir)
  }

  def from(pluginJpsRoot: Path, javaClassVersion: String, systemRootDir: Path): Either[String, SbtData] =
    for {
      sbtHome <- Either.cond(Files.exists(pluginJpsRoot), pluginJpsRoot, "Scala plugin jps directory does not exist: " + pluginJpsRoot)
      Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) = Jars.fromPluginJpsDirectory(sbtHome)
      sbtVersion <- readSbtVersionFrom(sbtInterfaceJar)
    } yield {
      val checksum = encodeHex(md5(compilerBridges.scala._2_10))
      val interfacesHome = compilerInterfacesDir(systemRootDir).resolve(sbtVersion + "-idea-" + checksum)
      SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)
    }

  private def readSbtVersionFrom(sbtInterfaceJar: Path): Either[String, String] = {
    val attributeName = "Implementation-Version"
    try {
      JarManifestUtils.readManifestAttribute(sbtInterfaceJar, attributeName) match {
        case Some(version) => Right(version)
        case None => Left(s"Unable to read attribute '$attributeName' from jar manifest, attribute missing")
      }
    } catch {
      case NonFatal(t) => Left(s"Unable to read sbt version from JVM classpath:\n$t")
    }
  }

  private def md5(file: Path): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val fileName = file.getFileName.toString
    val isSource = fileName.endsWith(".java") || fileName.endsWith(".scala")
    if (isSource) {
      Using.resource(Source.fromInputStream(Files.newInputStream(file), "UTF-8")) { source =>
        val text = source.mkString.replace("\r", "")
        md.digest(text.getBytes("UTF8"))
      }
    } else {
      val bytes = Files.readAllBytes(file)
      md.digest(bytes)
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
