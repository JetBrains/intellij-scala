package org.jetbrains.sbt

import java.io.{PrintWriter, FileWriter, File}
import scala.io.Source

/**
 * @author Pavel Fatin
 */
object Loader {
  private val JavaVM = canonicalPath(new File(new File(new File(System.getProperty("java.home")), "bin"), "java"))
  private val SbtLauncher = canonicalPath(new File("sbt-launch.jar"))
  private val SbtPlugin = canonicalPath(new File("target/scala-2.9.2/sbt-0.12/classes/"))
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).map(_.split("\\s+")).toSeq.flatten

  def load(project: File): Seq[String] = {
    val structureFile = createTempFile("sbt-structure", ".xml")
    val commandsFile = createTempFile("sbt-commands", ".lst")

    writeLinesTo(commandsFile,
      "set artifactPath := new File(\"" + canonicalPath(structureFile) + "\")",
      "apply -cp " + SbtPlugin + " org.jetbrains.sbt.Plugin")

    val commands = JavaVM +: JavaOpts :+ "-jar" :+ SbtLauncher :+ ("< " + canonicalPath(commandsFile))

    run(commands, project)

    assert(structureFile.exists, "File must be created: " + structureFile.getPath)

    read(structureFile)
  }

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')

  private def createTempFile(prefix: String, suffix: String): File = {
    val file = File.createTempFile(prefix, suffix)
    file.deleteOnExit()
    file
  }

  private def writeLinesTo(file: File, lines: String*) {
    val writer = new PrintWriter(new FileWriter(file))
    lines.foreach(writer.println(_))
    writer.close()
  }

  private def run(commands: Seq[String], directory: File) {
    val process = Runtime.getRuntime.exec(commands.toArray, null, directory)

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { it =>
        System.out.println("stdout: " + it)
      }
    }

    val stderrThread = inThread {
      Source.fromInputStream(process.getErrorStream).getLines().foreach { it =>
        System.err.println("stderr: " + it)
      }
    }

    process.waitFor()

    stdinThread.join()
    stderrThread.join()
  }

  private def inThread(block: => Unit): Thread = {
    val runnable = new Runnable {
      def run() {
        block
      }
    }
    val thread = new Thread(runnable)
    thread.start()
    thread
  }
}
