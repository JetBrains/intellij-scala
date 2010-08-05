package org.jetbrains.plugins.scala.config

import java.util.jar.JarFile
import java.util.Properties
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.openapi.util.io.FileUtil
import io.Source
import java.io.{StringBufferInputStream, File}

/**
 * Pavel.Fatin, 07.07.2010
 */

object FileAPI {
  def readProperty(archive: File, bundle: String, property: String) = {
    readEntry(archive, bundle).flatMap { content =>
      val stream = new StringBufferInputStream(content)
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
      case _ => None
    } finally {
      if(file != null) file.close()
    }
  }
  
  def exists(archive: File, entry: String): Boolean = {
    try {
      val file = new JarFile(archive)
      val result = file.getEntry(entry) != null
      file.close()
      return result
    } catch {
      case _ => return false
    }
  }
  
  def file(path: String) = new File(path)
  
  def optional(file: File): Option[File] = if(file.exists) Some(file) else None
  
  implicit def toRichFile(file: File) = new RichFile(file)
  
  implicit def toRichVirtualFile(virtualFile: VirtualFile) = new RichVirtualFile(virtualFile)
  
  
  class RichFile(delegate: File) {
    def /(path: String) = new File(delegate, path)
    def /(paths: Seq[String]) = paths.map(new File(delegate, _))
    def toLibraryRootURL = VfsUtil.getUrlForLibraryRoot(delegate)
    def parent: Option[File] = Option(delegate.getParent).map(new File(_))
    def findByName(name: String): Option[File] = delegate.listFiles.find(_.getName == name)
  }
  
  class RichVirtualFile(delegate: VirtualFile) {
    def toFile = VfsUtil.virtualToIoFile(delegate)
    def namedLike(name: String) = delegate.getName.startsWith(FileUtil.getNameWithoutExtension(name))
  }
}