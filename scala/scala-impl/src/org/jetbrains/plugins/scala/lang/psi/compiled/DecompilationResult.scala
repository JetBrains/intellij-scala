package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.reference.SoftReference

private[compiled] abstract sealed class DecompilationResult(implicit val timeStamp: Long)

private[compiled] object DecompilationResult {

  object Cache {

    private[this] val Key = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

    def apply(file: VirtualFile): DecompilationResult =
      file.getUserData(Key) match {
        case null => null
        case data => data.get
      }

    def update(file: VirtualFile, result: DecompilationResult): Unit = {
      file.putUserData(Key, new SoftReference(result))
    }
  }

  final case class Empty()
                        (implicit override val timeStamp: Long = 0L) extends DecompilationResult

  final case class Compiled(fileName: String, sources: String)
                           (implicit override val timeStamp: Long) extends DecompilationResult

  def apply(maybeNameAndSources: Option[(String, String)])
           (implicit timeStamp: Long): DecompilationResult =
    maybeNameAndSources.fold(Empty(): DecompilationResult) {
      case (fileName, sources) => Compiled(fileName, sources.replace("\r", ""))
    }

  def unapply(file: VirtualFile)
             (implicit contents: Array[Byte] = if (file.isDirectory) null else file.contentsToByteArray): Option[(String, String)] =
    try {
      for {
        bytes <- Option(contents)
        Compiled(fileName, sources) <- Some(file.decompile(bytes))
      } yield (fileName, sources)
    } catch {
      case _: IOException => None
    }

  def readFrom(inputStream: DataInputStream): Option[DecompilationResult] =
    try {
      val maybeNameAndSources = if (inputStream.readBoolean())
        Some((inputStream.readUTF(), ""))
      else
        None

      val result = apply(maybeNameAndSources)(inputStream.readLong())
      Some(result)
    } catch {
      case _: IOException => None
    }

  implicit class DecompilationResultExt(private val result: DecompilationResult) extends AnyVal {

    def writeTo(outputStream: DataOutputStream): Unit = try {
      val maybeFileName = result match {
        case Compiled(fileName, _) => Some(fileName)
        case _ => None
      }

      outputStream.writeBoolean(maybeFileName.isDefined)
      maybeFileName.foreach(outputStream.writeUTF)
      outputStream.writeLong(result.timeStamp)
      outputStream.close()
    } catch {
      case _: IOException =>
    }
  }

}