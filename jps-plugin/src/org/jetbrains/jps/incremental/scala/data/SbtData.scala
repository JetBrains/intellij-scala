package org.jetbrains.jps.incremental.scala.data

import java.io._
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala._

/**
 * @author Pavel Fatin
 */
case class SbtData(interfaceJar: File,
                   sourceJar: File,
                   interfacesHome: File,
                   javaClassVersion: String)

object SbtData {
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

        files.find(_.getName == "sbt-interface.jar")
          .toRight("No 'sbt-interface.jar' in SBT home directory")
          .flatMap { interfaceJar =>

          files.find(_.getName == "compiler-interface-sources.jar")
            .toRight("No 'compiler-interface-sources.jar' in SBT home directory")
            .flatMap { sourceJar =>

            readSbtVersionFrom(classLoader)
              .toRight("Unable to read SBT version from JVM classpath")
              .map { sbtVersion =>

              val checksum = DatatypeConverter.printHexBinary(md5(sourceJar))
              val interfacesHome = new File(compilerInterfacesDir, sbtVersion + "-idea-" + checksum)

              new SbtData(interfaceJar, sourceJar, interfacesHome, javaClassVersion)
            }
          }
        }
      }
    }
  }

  private def readSbtVersionFrom(classLoader: ClassLoader): Option[String] = {
    readProperty(classLoader, "xsbt.version.properties", "version").map { version =>
      if (version.endsWith("-SNAPSHOT")) {
        readProperty(getClass.getClassLoader, "xsbt.version.properties", "timestamp")
          .map(timestamp => version + "-" + timestamp)
          .getOrElse(version)
      } else {
        version
      }
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

}
