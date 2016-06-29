package org.jetbrains.plugins.scala.project

import java.io.File

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileListener, VirtualFileSystem}

/**
 * @author Pavel Fatin
 */
class AbsentLocalFile(url: String, path: String) extends VirtualFile {
  def getName = throw new UnsupportedOperationException()

  def getLength = throw new UnsupportedOperationException()

  def getFileSystem = AbsentLocalFileSystem

  def contentsToByteArray() = throw new UnsupportedOperationException()

  def getParent = throw new UnsupportedOperationException()

  def refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable) =
    throw new UnsupportedOperationException()

  def getTimeStamp = throw new UnsupportedOperationException()

  def getOutputStream(requestor: AnyRef, newModificationStamp: Long, newTimeStamp: Long) =
    throw new UnsupportedOperationException()

  def isDirectory = throw new UnsupportedOperationException()

  def getPath: String = path

  def isWritable = throw new UnsupportedOperationException()

  def isValid = false

  def getChildren = throw new UnsupportedOperationException()

  def getInputStream = throw new UnsupportedOperationException()

  override def getUrl: String = url
}

object AbsentLocalFileSystem extends VirtualFileSystem {
  def getProtocol = throw new UnsupportedOperationException()

  def renameFile(requestor: AnyRef, vFile: VirtualFile, newName: String) =
    throw new UnsupportedOperationException()

  def createChildFile(requestor: AnyRef, vDir: VirtualFile, fileName: String) =
    throw new UnsupportedOperationException()

  def addVirtualFileListener(virtualFileListener: VirtualFileListener) =
    throw new UnsupportedOperationException()

  def refreshAndFindFileByPath(s: String) = throw new UnsupportedOperationException()

  def copyFile(requestor: AnyRef, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String) =
    throw new UnsupportedOperationException()

  def refresh(asynchronous: Boolean) = throw new UnsupportedOperationException()

  def isReadOnly = throw new UnsupportedOperationException()

  def createChildDirectory(requestor: AnyRef, vDir: VirtualFile, dirName: String) =
    throw new UnsupportedOperationException()

  def removeVirtualFileListener(virtualFileListener: VirtualFileListener) =
    throw new UnsupportedOperationException()

  def moveFile(requestor: AnyRef, vFile: VirtualFile, newParent: VirtualFile) =
    throw new UnsupportedOperationException()

  def findFileByPath(path: String) = throw new UnsupportedOperationException()

  def deleteFile(requestor: AnyRef, vFile: VirtualFile) = throw new UnsupportedOperationException()

  override def extractPresentableUrl(path: String): String = path.replace('/', File.separatorChar)
}
