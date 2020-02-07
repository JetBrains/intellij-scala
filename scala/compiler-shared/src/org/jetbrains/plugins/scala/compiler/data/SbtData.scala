package org.jetbrains.plugins.scala.compiler.data

import java.io._
import java.security.MessageDigest
import java.util.jar.JarFile

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala.using

import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
 * @author Pavel Fatin
 */
case class SbtData(sbtInterfaceJar: File,
                   compilerInterfaceJar: File,
                   compilerBridges: SbtData.CompilerBridges,
                   interfacesHome: File,
                   javaClassVersion: String)

object SbtData {

  case class CompilerBridges(scala: ScalaSourceJars, dotty: DottyJars)

  /**
   * Contains sources of the scala compiler bridges.
   * We must compile sources to use bridges.
   */
  case class ScalaSourceJars(_2_10: File, _2_11: File, _2_13: File)

  /**
   * Contains already compiled dotty compiler bridges.
   */
  case class DottyJars(_0_22: File)

  val compilerInterfacesKey = "scala.compiler.interfaces.dir"

  private def compilerInterfacesDir = {
    def defaultDir = new File(new File(System.getProperty("user.home"), ".idea-build"), "scala-compiler-interfaces")

    val customPath = Option(System.getProperty(compilerInterfacesKey))
    customPath.map(new File(_)).getOrElse(defaultDir)
  }

  def from(pluginJpsRoot: File, javaClassVersion: String): Either[String, SbtData] =
    for {
      sbtHome  <- Either.cond(pluginJpsRoot.exists, pluginJpsRoot, "sbt home directory does not exist: " + pluginJpsRoot)
      sbtFiles <- Option(sbtHome.listFiles).toRight("Invalid sbt home directory: " + sbtHome.getPath)
      sbtData  <- from(sbtFiles, javaClassVersion)
    } yield sbtData

  private def from(sbtFiles: Seq[File], javaClassVersion: String): Either[String, SbtData] = {
    def fileWithName(name: String): Either[String, File] =
      sbtFiles.find(_.getName == name).toRight(s"No '$name' in sbt home directory")

    for {
      sbtInterfaceJar      <- fileWithName("sbt-interface.jar")
      compilerInterfaceJar <- fileWithName("compiler-interface.jar")
      scalaBridge_2_10     <- fileWithName("compiler-interface-sources-2.10.jar")
      scalaBridge_2_11     <- fileWithName("compiler-interface-sources-2.11.jar")
      scalaBridge_2_13     <- fileWithName("compiler-interface-sources-2.13.jar")
      dottyBridge_0_22     <- fileWithName("dotty-sbt-bridge-0.22.jar")
      sbtVersion           <- readSbtVersionFrom(sbtInterfaceJar)
    } yield {
      val checksum = encodeHex(md5(scalaBridge_2_10))
      val interfacesHome = new File(compilerInterfacesDir, sbtVersion + "-idea-" + checksum)
      val scalaBridgeSources = ScalaSourceJars(
        _2_10 = scalaBridge_2_10,
        _2_11 = scalaBridge_2_11,
        _2_13 = scalaBridge_2_13
      )
      val dottyBridges = DottyJars(
        _0_22 = dottyBridge_0_22
      )
      val compilerBridges = CompilerBridges(scalaBridgeSources, dottyBridges)

      new SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)
    }
  }

  private def readSbtVersionFrom(sbtInterfaceJar: File): Either[String, String] = {
    Try {
      using(new JarFile(sbtInterfaceJar)) {
        _.getManifest.getMainAttributes.getValue("Implementation-Version")
      }
    } match {
      case Success(version) => Right(version)
      case Failure(t) => Left(s"Unable to read sbt version from JVM classpath:\n$t")
    }
  }

  private def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      using(Source.fromFile(file, "UTF-8")) { source =>
        val text = source.mkString.replace("\r", "")
        md.digest(text.getBytes("UTF8"))
      }
    } else {
      using(new FileInputStream(file)) { input =>
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
