package org.jetbrains.jps.incremental.scala
package data

import java.io.{BufferedInputStream, File}
import org.jetbrains.jps.incremental.scala._

/**
 * @author Pavel Fatin
 */
case class SbtData(interfaceJar: File,
                   sourceJar: File,
                   interfacesHome: File,
                   javaClassVersion: String)

object SbtData {
  def from(classLoader: ClassLoader, pluginRoot: File, systemRoot: File, javaClassVersion: String): Either[String, SbtData] = {
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

              val interfacesHome = new File(new File(systemRoot, "scala-compiler-interfaces"), sbtVersion + "-idea")

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
}
