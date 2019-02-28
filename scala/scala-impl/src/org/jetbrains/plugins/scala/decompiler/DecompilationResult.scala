package org.jetbrains.plugins.scala
package decompiler

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.reference.SoftReference

private[decompiler] class DecompilationResult(val isScala: Boolean,
                                              val sourceName: String)
                                             (implicit val timeStamp: Long) {
  protected def rawSourceText: String = ""

  final def writeTo(outputStream: DataOutputStream): Unit = try {
    outputStream.writeBoolean(isScala)
    outputStream.writeUTF(sourceName)
    outputStream.writeLong(timeStamp)
    outputStream.close()
  } catch {
    case _: IOException =>
  }

}

private[decompiler] object DecompilationResult {

  private[this] val Key = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

  def empty(implicit timeStamp: Long = 0L) = new DecompilationResult(
    isScala = false,
    sourceName = ""
  )

  def apply(file: VirtualFile): DecompilationResult =
    file.getUserData(Key) match {
      case null => null
      case data => data.get()
    }

  def unapply(result: DecompilationResult): Some[(Boolean, String, String)] = Some(
    result.isScala,
    result.sourceName,
    result.rawSourceText.replace("\r", "")
  )

  def update(file: VirtualFile, result: DecompilationResult): Unit = {
    file.putUserData(Key, new SoftReference(result))
  }

  def readFrom(inputStream: DataInputStream): Option[DecompilationResult] =
    try {
      val isScala = inputStream.readBoolean()
      val sourceName = inputStream.readUTF()
      val timeStamp = inputStream.readLong()

      Some(new DecompilationResult(isScala, sourceName)(timeStamp))
    } catch {
      case _: IOException => None
    }
}