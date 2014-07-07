package org.jetbrains.sbt
package project.structure

import java.io._
import scala.xml.{Elem, XML}
import com.intellij.execution.process.OSProcessHandler
import java.util.jar.{JarEntry, JarFile}
import java.util.Properties
import SbtRunner._

/**
 * @author Pavel Fatin
 */
class SbtRunner(vmOptions: Seq[String], customLauncher: Option[File], customVM: Option[File]) {
  private val JavaHome = customVM.getOrElse(new File(System.getProperty("java.home")))
  private val JavaVM = JavaHome / "bin" / "java"
  private val LauncherDir = getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(LauncherDir / "sbt-launch.jar")
  private val DefaultSbtVersion = "0.13"

  def read(directory: File, download: Boolean)(listener: (String) => Unit): Either[Exception, Elem] = {
    checkFilePresence.fold(read0(directory, download)(listener))(it => Left(new FileNotFoundException(it)))
  }

  private def read0(directory: File, download: Boolean)(listener: (String) => Unit): Either[Exception, Elem] = {
    val sbtVersion = sbtVersionIn(directory)
            .orElse(implementationVersionOf(SbtLauncher))
            .getOrElse(DefaultSbtVersion)

    val majorSbtVersion = sbtVersion.split("\\.").take(2).mkString(".")

    read1(directory, majorSbtVersion, download, listener)
  }

  private def checkFilePresence: Option[String] = {
    val files = Stream("Java home" -> JavaHome, "SBT launcher" -> SbtLauncher)
    files.map((check _).tupled).flatten.headOption
  }

  private def check(entity: String, file: File) = (!file.exists()).option(s"$entity does not exist: $file")

  private def read1(directory: File, sbtVersion: String, download: Boolean, listener: (String) => Unit) = {
    val pluginFile = LauncherDir / s"sbt-structure-$sbtVersion.jar"
    val className = if (download) "ReadProjectAndRepository" else "ReadProject"

    usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      usingTempFile("sbt-commands", Some(".lst")) { commandsFile =>

        commandsFile.write(
          s"""set artifactPath := file("${path(structureFile)}")""",
          s"""apply -cp ${path(pluginFile)} org.jetbrains.sbt.$className""")

        val processCommands =
          path(JavaVM) +:
//                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" +:
                  "-Djline.terminal=jline.UnsupportedTerminal" +:
                  "-Dsbt.log.noformat=true" +:
                  vmOptions :+
                  "-jar" :+
                  path(SbtLauncher) :+
                  s"< ${path(commandsFile)}"

        try {
          val process = Runtime.getRuntime.exec(processCommands.toArray, null, directory)
          val output = handle(process, listener)
          (structureFile.length > 0).either(
            XML.load(structureFile.toURI.toURL))(new SbtException(output))
        } catch {
          case e: Exception => Left(e)
        }
      }}
  }

  private def handle(process: Process, listener: (String) => Unit): String = {
    val output = new StringBuilder()

    val processListener: (OutputType, String) => Unit = {
      case (OutputType.StdOut, text) =>
        if (text.contains("(q)uit")) {
          val writer = new PrintWriter(process.getOutputStream)
          writer.println("q")
          writer.close()
        } else {
          output.append(text)
          listener(text)
        }
      case (OutputType.StdErr, text) =>
        output.append(text)
        listener(text)
    }

    val handler = new OSProcessHandler(process, null, null)
    handler.addProcessListener(new ListenerAdapter(processListener))
    handler.startNotify()

    handler.waitFor()

    output.toString()
  }

  private def path(file: File): String = file.getAbsolutePath.replace('\\', '/')
}

object SbtRunner {
  def getSbtLauncherDir = {
    val file: File = jarWith[this.type]
    val deep = if (file.getName == "classes") 1 else 2
    (file << deep) / "launcher"
  }

  def getDefaultLauncher = getSbtLauncherDir / "sbt-launch.jar"

  private def implementationVersionOf(jar: File): Option[String] = {
    readManifestAttributeFrom(jar, "Implementation-Version")
  }

  private def readManifestAttributeFrom(file: File, name: String): Option[String] = {
    using(new JarFile(file)) { jar =>
      using(new BufferedInputStream(jar.getInputStream(new JarEntry("META-INF/MANIFEST.MF")))) { input =>
        val manifest = new java.util.jar.Manifest(input)
        val attributes = manifest.getMainAttributes
        Option(attributes.getValue(name))
      }
    }
  }

  private def sbtVersionIn(directory: File): Option[String] = {
    val propertiesFile = directory / "project" / "build.properties"
    if (propertiesFile.exists()) readPropertyFrom(propertiesFile, "sbt.version") else None
  }

  private def readPropertyFrom(file: File, name: String): Option[String] = {
    using(new BufferedInputStream(new FileInputStream(file))) { input =>
      val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }
  }
}