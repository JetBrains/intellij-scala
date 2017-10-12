package org.jetbrains.jps.incremental.scala
package data

import java.io._
import java.security.MessageDigest
import java.util.jar.JarFile

import com.intellij.openapi.util.io.FileUtil

import scala.util.{Failure, Success, Try}

/**
 * @author Pavel Fatin
 */
case class SbtData(sbtInterfaceJar: File,
                   compilerInterfaceJar: File,
                   sourceJars: SbtData.SourceJars,
                   interfacesHome: File,
                   javaClassVersion: String)

object SbtData {

  case class SourceJars(_2_10: File, _2_11: File)

  val compilerInterfacesKey = "scala.compiler.interfaces.dir"

  private def compilerInterfacesDir = {
    def defaultDir = new File(new File(System.getProperty("user.home"), ".idea-build"), "scala-compiler-interfaces")

    val customPath = Option(System.getProperty(compilerInterfacesKey))
    customPath.map(new File(_)).getOrElse(defaultDir)
  }

  def from(classLoader: ClassLoader, pluginRoot: File, javaClassVersion: String): Either[String, SbtData] = {
    Either.cond(pluginRoot.exists, pluginRoot,
      "SBT home directory does not exist: " + pluginRoot).flatMap { sbtHome =>

      Option(sbtHome.listFiles)
        .toRight("Invalid SBT home directory: " + sbtHome.getPath)
        .flatMap { files =>

          def fileWithName(name: String): Either[String, File] = {
            files.find(_.getName == name)
              .toRight(s"No '$name' in SBT home directory")
          }

          for {
            sbtInterfaceJar      <- fileWithName("sbt-interface.jar")
            compilerInterfaceJar <- fileWithName("compiler-interface.jar")
            source_2_10          <- fileWithName("compiler-interface-sources-2.10.jar")
            source_2_11          <- fileWithName("compiler-interface-sources-2.11.jar")
            sbtVersion           <- readSbtVersionFrom(sbtInterfaceJar)
          } yield {

            val checksum = encodeHex(md5(source_2_10))
            val interfacesHome = new File(compilerInterfacesDir, sbtVersion + "-idea-" + checksum)
            val sources = SourceJars(source_2_10, source_2_11)

            new SbtData(sbtInterfaceJar, compilerInterfaceJar, sources, interfacesHome, javaClassVersion)
          }
      }
    }
  }

  private def readSbtVersionFrom(sbtInterfaceJar: File): Either[String, String] = {
    Try {
      val manifest = new JarFile(sbtInterfaceJar).getManifest
      manifest.getMainAttributes.getValue("Implementation-Version")
    } match {
      case Success(version) => Right(version)
      case Failure(t) => Left(s"Unable to read SBT version from JVM classpath:\n$t")
    }
  }

  private def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      val text = scala.io.Source.fromFile(file, "UTF-8").mkString.replace("\r", "")
      md.digest(text.getBytes("UTF8"))
    } else {
      md.digest(FileUtil.loadBytes(new FileInputStream(file)))
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
