package org.jetbrains.plugins.scala
package project

import java.io.{Closeable, File, FileWriter, PrintWriter}

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil

import scala.reflect.ClassTag

/**
 * @author Pavel Fatin
 */
package object template {
  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }

  def usingTempFile[T](prefix: String, suffix: Option[String] = None)(block: File => T): T = {
    val file = FileUtil.createTempFile(prefix, suffix.orNull, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  def usingTempDirectory[T](prefix: String, suffix: Option[String] = None)(block: File => T): T = {
    val directory = FileUtil.createTempDirectory(prefix, suffix.orNull, true)
    try {
      block(directory)
    } finally {
      FileUtil.delete(directory)
    }
  }

  def usingTempDirectoryWithHandler[T, Z]
      (prefix: String, suffix: Option[String] = None)
      (handler1: PartialFunction[Throwable, T], handler2: PartialFunction[Throwable, Z])(block: File => T): T = {
    val directory = FileUtil.createTempDirectory(prefix, suffix.orNull, true)

    try {
      block(directory)
    } catch handler1 finally {
      try {
        FileUtil.delete(directory)
      } catch handler2
    }
  }

  def writeLinesTo(file: File, lines: String*) {
    using(new PrintWriter(new FileWriter(file))) { writer =>
      lines.foreach(writer.println)
      writer.flush()
    }
  }

  def jarWith[T : ClassTag]: File = {
    val tClass = implicitly[ClassTag[T]].runtimeClass

    Option(PathUtil.getJarPathForClass(tClass)).map(new File(_)).getOrElse {
      throw new RuntimeException("Jar file not found for class " + tClass.getName)
    }
  }

  implicit class FileExt(val delegate: File) extends AnyVal {
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
}
