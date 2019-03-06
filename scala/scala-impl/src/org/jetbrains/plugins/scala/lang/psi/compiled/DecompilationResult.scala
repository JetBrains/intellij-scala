package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.reference.SoftReference

private[compiled] abstract class DecompilationResult(val isScala: Boolean = false,
                                                     val sourceName: String = "")
                                                    (implicit val timeStamp: Long) {
  protected def rawSourceText: String = ""
}

private[compiled] object DecompilationResult {

  object Cache {

    private[this] val Key = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

    def apply(file: VirtualFile): DecompilationResult =
      file.getUserData(Key) match {
        case null => null
        case data => data.get()
      }

    def update(file: VirtualFile, result: DecompilationResult): Unit = {
      file.putUserData(Key, new SoftReference(result))
    }

  }

  def empty(implicit timeStamp: Long = 0L): DecompilationResult = new DecompilationResult() {}

  def unapply(file: VirtualFile)
             (implicit content: Array[Byte] = file.contentsToByteArray): Option[(String, String)] =
    try {
      val result = file.decompile(content)
      if (result.isScala) Some(result.sourceName, result.rawSourceText.replace("\r", ""))
      else None
    } catch {
      case _: IOException => None
    }

  def readFrom(inputStream: DataInputStream): Option[DecompilationResult] =
    try {
      val isScala = inputStream.readBoolean()
      val sourceName = inputStream.readUTF()
      val timeStamp = inputStream.readLong()

      Some(new DecompilationResult(isScala, sourceName)(timeStamp) {})
    } catch {
      case _: IOException => None
    }

  def writeTo(result: DecompilationResult,
              outputStream: DataOutputStream): Unit = try {
    outputStream.writeBoolean(result.isScala)
    outputStream.writeUTF(result.sourceName)
    outputStream.writeLong(result.timeStamp)
    outputStream.close()
  } catch {
    case _: IOException =>
  }
}