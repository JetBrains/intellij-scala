package org.jetbrains.sbt
package project.structure

import java.io._
import java.nio.charset.Charset
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile

import com.intellij.execution.process.OSProcessHandler
import org.jetbrains.sbt.project.structure.SbtRunner._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

/**
 * @author Pavel Fatin
 */
class SbtRunner(vmExecutable: File, vmOptions: Seq[String], environment: Map[String, String],
                customLauncher: Option[File], customStructureFile: Option[File]) {
  private val LauncherDir = getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(LauncherDir / "sbt-launch.jar")

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit =
    cancellationFlag.set(true)

  def read(directory: File, download: Boolean, resolveClassifiers: Boolean, resolveJavadocs: Boolean, resolveSbtClassifiers: Boolean)
          (listener: (String) => Unit): Try[Elem] = {

    val options = download.seq("download") ++
            resolveClassifiers.seq("resolveClassifiers") ++
            resolveJavadocs.seq("resolveJavadocs") ++
            resolveSbtClassifiers.seq("resolveSbtClassifiers")

    checkLauncherPresence.fold(read0(directory, options.mkString(", "))(listener))(it => Failure(new FileNotFoundException(it)))
  }

  private def read0(directory: File, options: String)(listener: (String) => Unit): Try[Elem] = {
    val sbtVersion = detectSbtVersion(directory, SbtLauncher)
    val majorSbtVersion = numbersOf(sbtVersion).take(2).mkString(".")

    if (compare(sbtVersion, SinceSbtVersion) < 0) {
      val message = s"SBT $SinceSbtVersion+ required. Please update the project definition"
      Failure(new UnsupportedOperationException(message))
    } else {
      read1(directory, majorSbtVersion, options, listener)
    }
  }

  private def checkLauncherPresence: Option[String] = check("SBT launcher", SbtLauncher)

  private def check(entity: String, file: File) = (!file.exists()).option(s"$entity does not exist: $file")

  private def read1(directory: File, sbtVersion: String, options: String, listener: (String) => Unit): Try[Elem] = {
    val pluginFile = customStructureFile.getOrElse(LauncherDir / s"sbt-structure-$sbtVersion.jar")

    usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      val sbtCommands = Seq(
        s"""set shellPrompt := { _ => "" }""",
        s"""set SettingKey[Option[File]]("sbt-structure-output-file") in Global := Some(file("${path(structureFile)}"))""",
        s"""set SettingKey[String]("sbt-structure-options") in Global := "$options" """,
        s"""apply -cp "${path(pluginFile)}" org.jetbrains.sbt.CreateTasks""",
        s"""*/*:dump-structure""",
        s"""exit""")

      val processCommandsRaw =
        path(vmExecutable) +:
        "-Djline.terminal=jline.UnsupportedTerminal" +:
        "-Dsbt.log.noformat=true" +:
        "-Dfile.encoding=UTF-8" +:
        (vmOptions ++ SbtOpts.loadFrom(directory)) :+
        "-jar" :+
        path(SbtLauncher)
      val processCommands = processCommandsRaw.filterNot(_.isEmpty)

      Try {
        val processBuilder = new ProcessBuilder(processCommands.asJava)
        processBuilder.directory(directory)
        environment.foreach { case (name, value) =>
          processBuilder.environment().put(name, value)
        }
        val process = processBuilder.start()
        using(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
          sbtCommands.foreach(writer.println)
          writer.flush()
          val result = handle(process, listener)
          result.map { output =>
            if (structureFile.length > 0)
              Success(XML.load(structureFile.toURI.toURL))
            else
              Failure(SbtException.fromSbtLog(output))
          }.getOrElse(Failure(new ImportCancelledException))
        }
      }.flatten
    }
  }

  private def handle(process: Process, listener: (String) => Unit): Option[String] = {
    val output = new StringBuffer()

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

    val handler = new OSProcessHandler(process, "SBT import", Charset.forName("UTF-8"))
    handler.addProcessListener(new ListenerAdapter(processListener))
    handler.startNotify()

    var processEnded = false
    while (!processEnded && !cancellationFlag.get())
      processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MSEC)

    if (!processEnded) {
      handler.setShouldDestroyProcessRecursively(false)
      handler.destroyProcess()
      None
    } else {
      Some(output.toString)
    }
  }

  private def path(file: File): String = file.getAbsolutePath.replace('\\', '/')
}

object SbtRunner {
  class ImportCancelledException extends Exception

  val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  def getSbtLauncherDir: File = {
    val file: File = jarWith[this.type]
    val deep = if (file.getName == "classes") 1 else 2
    (file << deep) / "launcher"
  }

  def getDefaultLauncher: File = getSbtLauncherDir / "sbt-launch.jar"

  private val SinceSbtVersion = "0.12.4"

  private def numbersOf(version: String): Seq[String] = version.split("\\.").toSeq

  private def compare(v1: String, v2: String): Int = numbersOf(v1).zip(numbersOf(v2)).foldLeft(0) {
    case (acc, (i1, i2)) if acc == 0 => i1.compareTo(i2)
    case (acc, _) => acc
  }

  private[structure] def detectSbtVersion(directory: File, sbtLauncher: File): String =
    sbtVersionIn(directory)
      .orElse(sbtVersionInBootPropertiesOf(sbtLauncher))
      .orElse(implementationVersionOf(sbtLauncher))
      .getOrElse(Sbt.LatestVersion)

  private def implementationVersionOf(jar: File): Option[String] =
    readManifestAttributeFrom(jar, "Implementation-Version")

  private def readManifestAttributeFrom(file: File, name: String): Option[String] = {
    val jar = new JarFile(file)
    try {
      Option(jar.getJarEntry("META-INF/MANIFEST.MF")).flatMap { entry =>
        val input = new BufferedInputStream(jar.getInputStream(entry))
        val manifest = new java.util.jar.Manifest(input)
        val attributes = manifest.getMainAttributes
        Option(attributes.getValue(name))
      }
    }
    finally {
      jar.close()
    }
  }

  private def sbtVersionInBootPropertiesOf(jar: File): Option[String] = {
    val appProperties = readSectionFromBootPropertiesOf(jar, sectionName = "app")
    for {
      name <- appProperties.get("name")
      if name == "sbt"
      versionStr <- appProperties.get("version")
      version <- "\\d+(\\.\\d+)+".r.findFirstIn(versionStr)
    } yield version
  }

  private def readSectionFromBootPropertiesOf(launcherFile: File, sectionName: String): Map[String, String] = {
    val Property = "^\\s*(\\w+)\\s*:(.+)".r.unanchored

    def findProperty(line: String): Option[(String, String)] = {
      line match {
        case Property(name, value) => Some((name, value.trim))
        case _ => None
      }
    }

    val jar = new JarFile(launcherFile)
    try {
      Option(jar.getEntry("sbt/sbt.boot.properties")).fold(Map.empty[String, String]) { entry =>
        val lines = scala.io.Source.fromInputStream(jar.getInputStream(entry)).getLines()
        val sectionLines = lines
          .dropWhile(_.trim != s"[$sectionName]").drop(1)
          .takeWhile(!_.trim.startsWith("["))
        sectionLines.flatMap(findProperty).toMap
      }
    } finally {
      jar.close()
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
