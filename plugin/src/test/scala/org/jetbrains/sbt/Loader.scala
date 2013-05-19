package org.jetbrains.sbt

import java.io.File
import scala.io.Source

/**
 * @author Pavel Fatin
 */
object Loader {
  private val JavaVM = canonicalPath(new File(new File(new File(System.getProperty("java.home")), "bin"), "java"))
  private val SbtLauncher = canonicalPath(new File("sbt-launch.jar"))
  private val SbtPlugin = canonicalPath(new File("target/scala-2.9.2/sbt-0.12/classes/"))
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).getOrElse("")

  def load(project: File): String = {
    val tempFile = File.createTempFile("sbt-structure", "xml")
    tempFile.deleteOnExit()

    val command = JavaVM + " " + JavaOpts + """ -jar """ + SbtLauncher +
      """ "; set artifactPath := new File(\"""" + canonicalPath(tempFile) +
      """\") ; apply -cp """ + SbtPlugin + """ org.jetbrains.sbt.Plugin""""

    run(command, project)

    assert(tempFile.exists, "File must be created: " + tempFile.getPath)

    Source.fromFile(tempFile).getLines().mkString("\n")
  }

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')

  private def run(command: String, directory: File) {
    val process = Runtime.getRuntime.exec(command, null, directory)

    val stdinThread = inThread {
      Source.fromInputStream(process.getInputStream).getLines().foreach { it =>
//        System.out.println("stdout: " + it)
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
