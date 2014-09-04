package org.jetbrains.plugins.scala.config

import java.io.{File, StringReader}
import java.util.Properties
import java.util.jar.JarFile

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore, VirtualFile}

import scala.io.Source

/**
 * Pavel.Fatin, 07.07.2010
 */

object FileAPI {
  def readProperty(archive: File, bundle: String, property: String) = {
    readEntry(archive, bundle).flatMap { content =>
      val stream = new StringReader(content)
      try {
        val properties = new Properties()
        properties.load(stream)
        Option(properties.getProperty(property))
      } finally {
        stream.close()
      }
    }
  }
  
  def readEntry(archive: File, entry: String) = {
    var file: JarFile = null
    try {
      file = new JarFile(archive)
      Option(file.getEntry(entry)).flatMap(entry => Option(file.getInputStream(entry))).flatMap { stream =>
          try {
            Some(Source.fromInputStream(stream).mkString)
          } finally {
            stream.close()
          }
      }
    } catch {
      case _: Exception => None
    } finally {
      if(file != null) file.close()
    }
  }
  
  def exists(archive: File, entry: String): Boolean = {
    try {
      val file = new JarFile(archive)
      val result = file.getEntry(entry) != null
      file.close()
      result
    } catch {
      case _: Exception => false
    }
  }
  
  def file(path: String) = new File(path)
  
  def optional(file: File): Option[File] = if(file.exists) Some(file) else None
  
  implicit class RichFile(val delegate: File) extends AnyVal {
    def /(path: String) = new File(delegate, path)
    def /(paths: Seq[String]) = paths.map(new File(delegate, _))
    def toLibraryRootURL = VfsUtil.getUrlForLibraryRoot(delegate)
    def parent: Option[File] = Option(delegate.getParent).map(new File(_))
    def findByName(name: String): Option[File] = delegate.listFiles.find(_.getName == name)
    def deleteForSure() {
      if(!delegate.delete()) delegate.deleteOnExit()
    }
  }
  
  implicit class RichVirtualFile(val delegate: VirtualFile) extends AnyVal {
    def toFile = VfsUtilCore.virtualToIoFile(delegate)
    def namedLike(name: String) = delegate.getName.startsWith(FileUtil.getNameWithoutExtension(name))
  }
}