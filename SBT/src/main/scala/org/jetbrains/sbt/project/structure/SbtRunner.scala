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
class SbtRunner(vmOptions: Seq[String], customLauncher: Option[File], vmExecutable: File) {
  private val LauncherDir = getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(LauncherDir / "sbt-launch.jar")
  private val DefaultSbtVersion = "0.13"
  private val SinceSbtVersion = "0.12.4"

  def read(directory: File, download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean)
          (listener: (String) => Unit): Either[Exception, Elem] = {

    val options = download.seq("download") ++
            resolveClassifiers.seq("resolveClassifiers") ++
            resolveSbtClassifiers.seq("resolveSbtClassifiers")

    checkFilePresence.fold(read0(directory, options.mkString(", "))(listener))(it => Left(new FileNotFoundException(it)))
  }

  private def read0(directory: File, options: String)(listener: (String) => Unit): Either[Exception, Elem] = {
    val sbtVersion = sbtVersionIn(directory)
            .orElse(implementationVersionOf(SbtLauncher))
            .getOrElse(DefaultSbtVersion)

    val majorSbtVersion = numbersOf(sbtVersion).take(2).mkString(".")

    if (compare(sbtVersion, SinceSbtVersion) < 0) {
      val message = s"SBT $SinceSbtVersion+ required. Please update the project definition"
      Left(new UnsupportedOperationException(message))
    } else {
      read1(directory, majorSbtVersion, options, listener)
    }
  }

  private def checkFilePresence: Option[String] = {
    val files = Stream("SBT launcher" -> SbtLauncher)
    files.map((check _).tupled).flatten.headOption
  }

  private def check(entity: String, file: File) = (!file.exists()).option(s"$entity does not exist: $file")

  private def read1(directory: File, sbtVersion: String, options: String, listener: (String) => Unit) = {
    val pluginFile = LauncherDir / s"sbt-structure-$sbtVersion.jar"

    usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      usingTempFile("sbt-commands", Some(".lst")) { commandsFile =>

        commandsFile.write(
          s"""set artifactPath := file("${path(structureFile)}")""",
          s"""set artifactClassifier := Some("$options")""",
          s"""apply -cp "${path(pluginFile)}" org.jetbrains.sbt.ReadProject""")

        val processCommands =
          path(vmExecutable) +:
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

  private def numbersOf(version: String): Seq[String] = version.split("\\.").toSeq

  private def compare(v1: String, v2: String): Int = numbersOf(v1).zip(numbersOf(v2)).foldLeft(0) {
    case (acc, (i1, i2)) if acc == 0 => i1.compareTo(i2)
    case (acc, _) => acc
  }

  private def implementationVersionOf(jar: File): Option[String] = {
    readManifestAttributeFrom(jar, "Implementation-Version")
  }

  private def readManifestAttributeFrom(file: File, name: String): Option[String] = {
    val jar = new JarFile(file)
    try {
      using(new BufferedInputStream(jar.getInputStream(new JarEntry("META-INF/MANIFEST.MF")))) { input =>
        val manifest = new java.util.jar.Manifest(input)
        val attributes = manifest.getMainAttributes
        Option(attributes.getValue(name))
      }
    }
    finally {
      if (jar.isInstanceOf[Closeable]) {
        jar.close()
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