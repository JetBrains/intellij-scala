package org.jetbrains.sbt
package project.structure

import java.io._
import java.nio.charset.Charset
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.{JarEntry, JarFile}

import com.intellij.execution.process.OSProcessHandler
import org.jetbrains.sbt.project.structure.SbtRunner._

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.xml.{Elem, XML}

/**
 * @author Pavel Fatin
 */
class SbtRunner(vmExecutable: File, vmOptions: Seq[String], environment: Map[String, String],
                customLauncher: Option[File], customStructureDir: Option[String]) {
  private val LauncherDir = getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(LauncherDir / "sbt-launch.jar")
  private val DefaultSbtVersion = "0.13.8"
  private val SinceSbtVersion = "0.12.4"

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit =
    cancellationFlag.set(true)

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
    val pluginFile = customStructureDir.map(new File(_)).getOrElse(LauncherDir) / s"sbt-structure-$sbtVersion.jar"

    usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      val sbtCommands = Seq(
        s"""set shellPrompt := { _ => "" }""",
        s"""set artifactPath := file("${path(structureFile)}")""",
        s"""set artifactClassifier := Some("$options")""",
        s"""apply -cp "${path(pluginFile)}" org.jetbrains.sbt.ReadProject""",
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

      try {
        val processBuilder = new ProcessBuilder(processCommands.asJava)
        processBuilder.directory(directory)
        environment.foreach { case (name, value) =>
          processBuilder.environment().put(name, value)
        }
        val process = processBuilder.start()
        using(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
          sbtCommands.foreach(writer.println)
        }
        val result = handle(process, listener)
        result.map { output =>
          (structureFile.length > 0).either(
            XML.load(structureFile.toURI.toURL))(SbtException.fromSbtLog(output))
        }.getOrElse(Left(new ImportCancelledException))
      } catch {
        case e: Exception => Left(e)
      }
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

    val handler = new OSProcessHandler(process, null, Charset.forName("UTF-8"))
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

  def getDefaultLauncher = getSbtLauncherDir / "sbt-launch.jar"

  private def numbersOf(version: String): Seq[String] = version.split("\\.").toSeq

  private def compare(v1: String, v2: String): Int = numbersOf(v1).zip(numbersOf(v2)).foldLeft(0) {
    case (acc, (i1, i2)) if acc == 0 => i1.compareTo(i2)
    case (acc, _) => acc
  }

  private[structure] def implementationVersionOf(jar: File): Option[String] = {
    val appProperties = BootPropertiesReader(jar, sectionName = "app")
    for {
      name <- appProperties.find(_.name == "name").map(_.value)
      versionStr <- appProperties.find(_.name == "version").map(_.value)
      version <- parseVersionFromBootProperties(versionStr)
      if name == "sbt"
    } yield version
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

  private def parseVersionFromBootProperties(versionStr: String): Option[String] = {
    val substStart = "(?:\\$\\{sbt\\.version-)?"
    val readStart = "(?:read\\(sbt\\.version\\)\\[)?"
    val readEnd = "(?:\\])?"
    val substEnd = "(?:\\})?"
    val versionPattern = (substStart + readStart + "[0-9\\.]+" + readEnd + substEnd).r
    versionStr match {
      case versionPattern(version) => Some(version)
      case _ => None
    }
  }
}

object BootPropertiesReader {

  final case class Property(name: String, value: String)

  final case class Section(name: String, properties: Seq[Property])

  def apply(file: File, sectionName: String): Seq[Property] =
    apply(file).find(_.name == sectionName).fold(Seq.empty[Property])(_.properties)

  def apply(file: File): Seq[Section] = {
    val jar = new JarFile(file)
    try {
      using(jar.getInputStream(new JarEntry("sbt/sbt.boot.properties"))) { input =>
        val lines = scala.io.Source.fromInputStream(input).getLines()
        readLines(lines)
      }
    }
    finally {
      if (jar.isInstanceOf[Closeable]) {
        jar.close()
      }
    }
  }

  type ReaderState = (Seq[Section], Option[String], Seq[Property])

  private val emptyReaderState: ReaderState = (Seq.empty, None, Seq.empty)

  private def readLines(lines: Iterator[String]): Seq[Section] = {
    val (prevSections, lastSection, lastProperties) =
      lines.map(_.trim).foldLeft[ReaderState](emptyReaderState)((acc, line) => readLine(line, acc))
    appendSection(prevSections, lastSection, lastProperties)
  }

  private def readLine(line: String, state: ReaderState): ReaderState = state match {
    case (prevSections, lastSection, lastProperties) =>
      if (line.startsWith("#")) {
        state
      } else if (line.startsWith("[")) {
        val newSections = appendSection(prevSections, lastSection, lastProperties)
        val braceEnd = line.indexOf(']')
        val section = (braceEnd != 1).option(line.substring(1, braceEnd+1).trim)
        (newSections, section, Seq.empty)
      } else {
        line.split(":", 2) match {
          case Array(name, value) => (prevSections, lastSection, lastProperties :+ Property(name, value))
          case _ => state
        }
      }
  }

  private def appendSection(sections: Seq[Section], sectionName: Option[String], properties: Seq[Property]): Seq[Section] =
    sections ++ sectionName.map(name => Section(name, properties)).toSeq
}

