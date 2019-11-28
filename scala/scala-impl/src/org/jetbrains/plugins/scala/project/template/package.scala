package org.jetbrains.plugins.scala
package project

import java.io._

import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.util.{Key, io}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.{PathUtil, net}

import scala.collection.JavaConverters

/**
 * @author Pavel Fatin
 */
package object template {

  private val DefaultCommands = Array(
    "java",
    "-Djline.terminal=jline.UnsupportedTerminal",
    "-Dsbt.log.noformat=true"
  )

  private abstract class SBTProcessListener extends ProcessAdapter {

    private val builder: StringBuilder = StringBuilder.newBuilder

    final def text: String = builder.toString

    protected def onText(text: String): Unit

    override final def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      val text = event.getText.trim
      onText(text)
      builder ++= text
    }
  }

  def createTempSbtProject(version: String,
                           preUpdateCommands: Seq[String] = Seq.empty,
                           postUpdateCommands: Seq[String] = Seq.empty)
                          (action: String => Unit): Unit =
    usingTempFile("sbt-commands") { file =>
      writeLinesTo(file)(
        (s"""set scalaVersion := "$version"""" +: preUpdateCommands :+ "updateClassifiers") ++
          postUpdateCommands: _*
      )

      usingTempDirectory("sbt-project") { dir =>
        val process = Runtime.getRuntime.exec(
          DefaultCommands ++ vmOptions ++ launcherOptions(file.getAbsolutePath),
          null,
          dir
        )

        val listener: SBTProcessListener = action(_)

        val handler = new OSProcessHandler(process, "sbt-based downloader", null)
        handler.addProcessListener(listener)
        handler.startNotify()
        handler.waitFor()

        val text = listener.text
        process.exitValue match {
          case 0 => text
          case _ => throw new RuntimeException(text)
        }
      }
    }


  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }

  import io.FileUtil._

  def usingTempFile[T](prefix: String, suffix: String = null)(block: File => T): T = {
    val file = createTempFile(prefix, suffix, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  def usingTempDirectory[T](prefix: String)(block: File => T): T = {
    val directory = createTempDirectory(prefix, null, true)
    try {
      block(directory)
    } finally {
      delete(directory)
    }
  }

  def writeLinesTo(file: File)
                  (lines: String*): Unit = {
    using(new PrintWriter(new FileWriter(file))) { writer =>
      lines.foreach(writer.println)
      writer.flush()
    }
  }

  implicit class FileExt(private val delegate: File) extends AnyVal {
    def /(path: String): File = new File(delegate, path)

    def /(paths: Seq[String]): File = paths.foldLeft(delegate)(_ / _)

    def parent: Option[File] = Option(delegate.getParentFile)

    def children: Seq[File] = Option(delegate.listFiles).map(_.toSeq).getOrElse(Seq.empty)

    def directories: Seq[File] = children.filter(_.isDirectory)

    def files: Seq[File] = children.filter(_.isFile)

    def findByName(name: String): Option[File] = children.find(_.getName == name)

    def allFiles: Stream[File] = {
      val (files, directories) = children.toStream.span(_.isFile)
      files #::: directories.flatMap(_.allFiles)
    }

    def toLibraryRootURL: String = VfsUtil.getUrlForLibraryRoot(delegate)
  }

  private[this] def launcherOptions(path: String) =
    new File(PathUtil.getJarPathForClass(getClass)).getParentFile.getParentFile / "launcher" / "sbt-launch.jar" match {
      case launcher if launcher.exists => Seq("-jar", launcher.getAbsolutePath, "< " + path)
      case launcher => throw new FileNotFoundException("Jar file not found for class: " + launcher.getPath)
    }

  private[this] def vmOptions = {
    import JavaConverters._
    val proxyOpts = net.HttpConfigurable.getInstance
      .getJvmProperties(false, null)
      .asScala
    val javaOpts = Seq(sysprop("java.io.tmpdir")).flatten
    (proxyOpts ++ javaOpts)
      .map { pair =>
        "-D" + pair.getFirst + "=" + pair.getSecond
      }
  }

  private def sysprop(key: String) = {
    import com.intellij.openapi.util.Pair
    Option(System.getProperty(key)).map(value => Pair.pair(key, value))
  }
}
