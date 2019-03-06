package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.util.{Key, text}
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

private[compiled] sealed abstract class DecompilationResult(val isScala: Boolean = false,
                                                            val sourceName: String = "")
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
    try {
      val result = decompile(file, bytes)
      if (result.isScala) Some(result.sourceName, text.StringUtil.convertLineSeparators(result.rawSourceText))
      else None
    } catch {
      case _: IOException => None
    }

  def decompile(file: VirtualFile, bytes: Array[Byte] = null): DecompilationResult = {
    val maybeContent = bytes match {
      case null => if (file.isDirectory) None else Some(() => file.contentsToByteArray)
      case content => Some(() => content)
    }

    maybeContent match {
      case Some(content) if file.isInstanceOf[VirtualFileWithId] =>
        import ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute

        implicit val timeStamp: Long = file.getTimeStamp
        var cached = Cache(file)

        if (cached == null || cached.timeStamp != timeStamp) {
          val maybeResult = for {
            attribute <- DecompilerFileAttribute
            readAttribute <- Option(attribute.readAttribute(file))

            result <- readFrom(readAttribute)
            if result.timeStamp == timeStamp
          } yield result

          def decompile() = decompiler.Decompiler(file.getName, content())

          cached = maybeResult match {
            case Some(result) if result.isScala =>
              new DecompilationResult(isScala = true, result.sourceName) {
                override protected lazy val rawSourceText: String = decompile().fold("")(_._2)
              }
            case Some(result) =>
              new DecompilationResult(sourceName = result.sourceName) {}
            case _ =>
              val result = decompile().fold(empty()) {
                case (sourceName, sourceText) =>
                  new DecompilationResult(isScala = true, sourceName) {
                    override protected val rawSourceText: String = sourceText
                  }
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
      case _ => empty()
    }
  }

  private[this] def empty(implicit timeStamp: Long = 0L): DecompilationResult = new DecompilationResult() {}

  private[this] def readFrom(inputStream: DataInputStream): Option[DecompilationResult] =
    try {
      val isScala = inputStream.readBoolean()
      val sourceName = inputStream.readUTF()
      val timeStamp = inputStream.readLong()

      Some(new DecompilationResult(isScala, sourceName)(timeStamp) {})
    } catch {
      case _: IOException => None
    }

  private[this] def writeTo(result: DecompilationResult,
                            outputStream: DataOutputStream): Unit =
    try {
      outputStream.writeBoolean(result.isScala)
      outputStream.writeUTF(result.sourceName)
      outputStream.writeLong(result.timeStamp)
      outputStream.close()
    } catch {
      case _: IOException =>
    }
}