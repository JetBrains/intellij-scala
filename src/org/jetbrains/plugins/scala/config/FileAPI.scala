package org.jetbrains.plugins.scala.config

import java.io.File
import java.util.jar.JarFile
import java.util.Properties
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.openapi.util.io.FileUtil

/**
 * Pavel.Fatin, 07.07.2010
 */

trait FileAPI {
  protected def readProperty(archive: File, bundle: String, property: String) = {
    val file = new JarFile(archive)
    try {
      Option(file.getEntry(bundle)).flatMap(entry => Option(file.getInputStream(entry))).flatMap { stream =>
          val properties = new Properties()
          try {
            properties.load(stream)
          } finally {
            stream.close()
          }
          Option(properties.getProperty(property))
      }
    } finally {
      file.close()
    }
  }
  
  protected def file(path: String) = new File(path)
  
  protected def optional(file: File): Option[File] = if(file.exists) Some(file) else None
  
  implicit protected def toRichFile(file: File) = new RichFile(file)
  
  implicit protected def toRichVirtualFile(virtualFile: VirtualFile) = new RichVirtualFile(virtualFile)
  
  
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