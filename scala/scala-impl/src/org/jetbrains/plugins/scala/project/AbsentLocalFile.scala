package org.jetbrains.plugins.scala.project

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileListener, VirtualFileSystem}

class AbsentLocalFile(url: String, path: String) extends VirtualFile {
  override def getName: Nothing = throw new UnsupportedOperationException()

  override def getLength: Nothing = throw new UnsupportedOperationException()

  override def getFileSystem: VirtualFileSystem = AbsentLocalFileSystem

  override def contentsToByteArray(): Nothing = throw new UnsupportedOperationException()

  override def getParent: Nothing = throw new UnsupportedOperationException()

  override def refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable): Unit =
    throw new UnsupportedOperationException()

  override def getTimeStamp: Nothing = throw new UnsupportedOperationException()

  override def getOutputStream(requestor: AnyRef, newModificationStamp: Long, newTimeStamp: Long): Nothing =
    throw new UnsupportedOperationException()

  override def isDirectory: Nothing = throw new UnsupportedOperationException()

  override def getPath: String = path

  override def isWritable: Nothing = throw new UnsupportedOperationException()

  override def isValid = false

  override def getChildren: Nothing = throw new UnsupportedOperationException()

  override def getInputStream: Nothing = throw new UnsupportedOperationException()

  override def getUrl: String = url
}

object AbsentLocalFileSystem extends VirtualFileSystem {
  override def getProtocol: Nothing = throw new UnsupportedOperationException()

  override def renameFile(requestor: AnyRef, vFile: VirtualFile, newName: String): Unit =
    throw new UnsupportedOperationException()

  override def createChildFile(requestor: AnyRef, vDir: VirtualFile, fileName: String): Nothing =
    throw new UnsupportedOperationException()

  override def addVirtualFileListener(virtualFileListener: VirtualFileListener): Unit =
    throw new UnsupportedOperationException()

  override def refreshAndFindFileByPath(s: String): Nothing = throw new UnsupportedOperationException()

  override def copyFile(requestor: AnyRef, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): Nothing =
    throw new UnsupportedOperationException()

  override def refresh(asynchronous: Boolean): Unit = throw new UnsupportedOperationException()

  override def isReadOnly: Nothing = throw new UnsupportedOperationException()

  override def createChildDirectory(requestor: AnyRef, vDir: VirtualFile, dirName: String): Nothing =
    throw new UnsupportedOperationException()

  override def removeVirtualFileListener(virtualFileListener: VirtualFileListener): Unit =
    throw new UnsupportedOperationException()

  override def moveFile(requestor: AnyRef, vFile: VirtualFile, newParent: VirtualFile): Unit =
    throw new UnsupportedOperationException()

  override def findFileByPath(path: String): Nothing = throw new UnsupportedOperationException()

  override def deleteFile(requestor: AnyRef, vFile: VirtualFile): Unit = throw new UnsupportedOperationException()

  override def extractPresentableUrl(path: String): String = path.replace('/', java.io.File.separatorChar)
}
