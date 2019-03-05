package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

private[compiled] abstract sealed class DecompilationResult(implicit val timeStamp: Long)

private[compiled] object DecompilationResult {

  final case class Empty()
                        (implicit override val timeStamp: Long = 0L) extends DecompilationResult

  final case class Compiled(fileName: String, sources: String)
                           (implicit override val timeStamp: Long) extends DecompilationResult

  private def apply(maybeNameAndSources: Option[(String, String)])
                   (implicit timeStamp: Long): DecompilationResult =
    maybeNameAndSources.fold(Empty(): DecompilationResult) {
      case (fileName, sources) => Compiled(fileName, sources.replace("\r", ""))
    }

  def unapply(file: VirtualFile)
             (implicit contents: Array[Byte] = null): Option[(String, String)] = file match {
    case _: VirtualFileWithId =>
      try {
        for {
          bytes <- contents match {
            case null => if (file.isDirectory) None else Some(file.contentsToByteArray)
            case _ => Some(contents)
          }

          Compiled(fileName, sources) <- Some(decompile(file)(bytes, file.getTimeStamp))
        } yield (fileName, sources)
      } catch {
        case _: IOException => None
      }
    case _ => None
  }

  private[this] def decompile(file: VirtualFile)
                             (implicit bytes: Array[Byte],
                              timeStamp: Long) = file match {
    case Cache(cached) if cached.timeStamp == timeStamp => cached
    case _ =>
      import ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute

      val maybeResult = for {
        attribute <- DecompilerFileAttribute
        readAttribute = attribute.readAttribute(file)
        if readAttribute != null

        result <- Cache.readFrom(readAttribute)
        if result.timeStamp == timeStamp
      } yield result

      val result = maybeResult match {
        case Some(Empty()) => Empty()
        case _ =>
          val result = DecompilationResult {
            decompiler.Decompiler(file.getName, bytes)
          }

          for {
            attribute <- DecompilerFileAttribute
            if maybeResult.isEmpty
          } Cache.writeTo(result, attribute.writeAttribute(file))

          result
      }

      Cache(file) = result
      result
  }

  private[this] object Cache {

    private[this] val Key = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

    def unapply(file: VirtualFile): Option[DecompilationResult] =
      file.getUserData(Key) match {
        case null => None
        case data => Option(data.get)
      }

    def update(file: VirtualFile, result: DecompilationResult): Unit = {
      file.putUserData(Key, new SoftReference(result))
    }

    def readFrom(inputStream: DataInputStream): Option[DecompilationResult] = try {
      val maybeNameAndSources = if (inputStream.readBoolean())
        Some((inputStream.readUTF(), ""))
      else
        None

      val result = DecompilationResult(maybeNameAndSources)(inputStream.readLong())
      Some(result)
    } catch {
      case _: IOException => None
    }

    def writeTo(result: DecompilationResult,
                outputStream: DataOutputStream): Unit = try {
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