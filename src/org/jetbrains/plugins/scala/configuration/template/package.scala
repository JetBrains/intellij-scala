package org.jetbrains.plugins.scala
package configuration

import java.io.File

import com.intellij.openapi.vfs.VfsUtil

/**
 * @author Pavel Fatin
 */
package object template {
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
