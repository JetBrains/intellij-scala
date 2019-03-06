package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.{Key, text}
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

private[compiled] sealed abstract class DecompilationResult(val isScala: Boolean,
                                                            val sourceName: String)
                                                           (implicit val timeStamp: Long) {
  protected def rawSourceText: String = ""
}

private[compiled] object DecompilationResult {

  private[this] object Cache {

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

  def unapply(file: VirtualFile)
             (implicit bytes: Array[Byte] = null): Option[(String, String)] =
    tryDecompile(file, bytes).map { result =>
      (result.sourceName, text.StringUtil.convertLineSeparators(result.rawSourceText))
    }

  def tryDecompile(file: VirtualFile, bytes: Array[Byte] = null): Option[DecompilationResult] =
    try {
      val maybeContent = bytes match {
        case null => if (file.isDirectory) None else Some(() => file.contentsToByteArray)
        case content => Some(() => content)
      }

      for {
        content <- maybeContent
        if file.isInstanceOf[VirtualFileWithId]

        result = decompile(file) {
          decompiler.Decompiler(file.getName, content())
        }(file.getTimeStamp)
        if result.isScala
      } yield result
    } catch {
      case _: IOException => None
    }

  private[this] def decompile(file: VirtualFile)
                             (decompile: => Option[(String, String)])
                             (implicit timeStamp: Long) = {
    import ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute

    var cached = Cache(file)

    if (cached == null || cached.timeStamp != timeStamp) {
      val maybeResult = for {
        attribute <- DecompilerFileAttribute
        readAttribute <- Option(attribute.readAttribute(file))

        result <- readFrom(readAttribute)
        if result.timeStamp == timeStamp
      } yield result

      cached = maybeResult match {
        case Some(result) =>
          new DecompilationResult(result.isScala, result.sourceName) {
            override protected lazy val rawSourceText: String = {
              val decompiled = if (result.isScala) decompile else None
              decompiled.fold("")(_._2)
            }
          }
        case _ =>
          val decompiled = decompile
          val (sourceName, sourceText) = decompile.getOrElse("", "")

          val result: DecompilationResult = new DecompilationResult(decompiled.isDefined, sourceName) {
            override protected val rawSourceText: String = sourceText
          }

          for {
            attribute <- DecompilerFileAttribute
            outputStream = attribute.writeAttribute(file)
          } writeTo(result, outputStream)

          result
      }

      Cache(file) = cached
    }

    cached
  }

  private[this] def readFrom(inputStream: DataInputStream) = try {
    val isScala = inputStream.readBoolean()
    val sourceName = inputStream.readUTF()
    val timeStamp = inputStream.readLong()

    Some(new DecompilationResult(isScala, sourceName)(timeStamp) {})
  } catch {
    case _: IOException => None
  }

  private[this] def writeTo(result: DecompilationResult,
                            outputStream: DataOutputStream): Unit = try {
    outputStream.writeBoolean(result.isScala)
    outputStream.writeUTF(result.sourceName)
    outputStream.writeLong(result.timeStamp)
    outputStream.close()
  } catch {
    case _: IOException =>
  }
}