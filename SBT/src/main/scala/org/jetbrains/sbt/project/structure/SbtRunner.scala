package org.jetbrains.sbt
package project.structure

import java.io.{FileNotFoundException, PrintWriter, File}
import scala.xml.{Elem, XML}
import com.intellij.execution.process.OSProcessHandler

/**
 * @author Pavel Fatin
 */
class SbtRunner(ideaSystem: File, vmOptions: Seq[String], customLauncher: Option[File], customVM: Option[File]) {
  private val JavaHome = customVM.getOrElse(new File(System.getProperty("java.home")))
  private val JavaVM = JavaHome / "bin" / "java"
  private val LauncherDir = SbtRunner.getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(LauncherDir / "sbt-launch.jar")

  def read(directory: File, download: Boolean)(listener: (String) => Unit): Either[Exception, Elem] = {
    val files = Stream("Java home" -> JavaHome, "SBT launcher" -> SbtLauncher)
    val problem = files.map((check _).tupled).flatten.headOption
    problem.fold(read0(directory, download, listener))(it => Left(new FileNotFoundException(it)))
  }

  private def check(entity: String, file: File) = (!file.exists()).option(s"$entity does not exist: $file")

  private def read0(directory: File, download: Boolean, listener: (String) => Unit) = {
    val sbtBase = ideaSystem / "SBT"

    createGlobalConfigurationWithin(sbtBase)

    usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      usingTempFile("sbt-commands", Some(".lst")) { commandsFile =>

        commandsFile.write(
          s"""set artifactPath := file("${path(structureFile)}")""",
          if (download) "read-project-and-repository" else "read-project")

        val processCommands =
          path(JavaVM) +:
//                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" +:
                  "-Djline.terminal=jline.UnsupportedTerminal" +:
                  "-Dsbt.log.noformat=true" +:
                  s"-Dsbt.global.base=${sbtBase.canonicalPath}" +:
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

  private def createGlobalConfigurationWithin(base: File) {
    val lines = Seq(
      """resolvers += "sbt-releases" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"""",
      "",
      s"""addSbtPlugin("org.jetbrains" % "sbt-structure" % "${Sbt.StructurePluginVersion}")""")

    val pluginsFiles = Seq(base / "plugins" / "build.sbt", base / "0.13" / "plugins" / "build.sbt")

    pluginsFiles.foreach { file =>
      file.getParentFile.mkdirs()
      file.write(lines: _*)
    }
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
}