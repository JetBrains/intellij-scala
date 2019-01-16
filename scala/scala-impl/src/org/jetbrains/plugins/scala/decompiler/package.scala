package org.jetbrains.plugins.scala

import java.io.{DataInputStream, DataOutputStream, IOException}

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

package object decompiler {

  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil")

  val DECOMPILER_VERSION = 306
  private val SCALA_DECOMPILER_FILE_ATTRIBUTE = new FileAttribute("_is_scala_compiled_new_key_", DECOMPILER_VERSION, true)

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private def findAttributes =
    if (ScalaLoader.isUnderUpsource) None
    else Some(SCALA_DECOMPILER_FILE_ATTRIBUTE)

  private[decompiler] class DecompilationResult(val isScala: Boolean, val sourceName: String)
                                               (implicit val timeStamp: Long) {
    def sourceText: String = rawSourceText.replace("\r", "")

    def rawSourceText: String = ""

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

    def apply(virtualFile: VirtualFile): DecompilationResult =
      virtualFile.getUserData(Key) match {
        case null => null
        case data => data.get()
      }

    def update(virtualFile: VirtualFile, result: DecompilationResult): Unit = {
      virtualFile.putUserData(Key, new SoftReference(result))
    }

    def readFrom(inputStream: DataInputStream): Option[DecompilationResult] = inputStream match {
      case null => None
      case _ =>
        try {
          val isScala = inputStream.readBoolean()
          val sourceName = inputStream.readUTF()
          val timeStamp = inputStream.readLong()

          Some(new DecompilationResult(isScala, sourceName)(timeStamp))
        } catch {
          case _: IOException => None
        }
    }

    def empty(implicit timeStamp: Long = 0L): DecompilationResult =
      new DecompilationResult(isScala = false, sourceName = "")
  }

  def sourceName(file: VirtualFile): String =
    decompile(file)().sourceName

  private[decompiler] def isScalaFile(file: VirtualFile): Boolean =
    try {
      decompile(file)().isScala
    } catch {
      case _: IOException => false
    }

  private[decompiler] def decompile(file: VirtualFile)
                                   (bytes: => Array[Byte] = file.contentsToByteArray): DecompilationResult = file match {
    case _: VirtualFileWithId =>
      implicit val timeStamp: Long = file.getTimeStamp
      var cached = DecompilationResult(file)

      if (cached == null || cached.timeStamp != timeStamp) {
        val maybeResult = for {
          attributes <- findAttributes
          readAttribute = attributes.readAttribute(file)
          result <- DecompilationResult.readFrom(readAttribute)

          if result.timeStamp == timeStamp
        } yield result

        val fileName = file.getName
        cached = maybeResult match {
          case Some(result) =>
            new DecompilationResult(result.isScala, result.sourceText) {
              override lazy val rawSourceText: String = decompileInner(fileName, bytes).fold("")(_._2)
            }
          case _ =>
            val result = decompileInner(fileName, bytes).fold(DecompilationResult.empty()) {
              case (sourceFileName, decompiledSourceText) =>
                new DecompilationResult(isScala = true, sourceFileName) {
                  override def rawSourceText: String = decompiledSourceText
                }
            }
            for {
              attributes <- findAttributes
              outputStream = attributes.writeAttribute(file)
            } result.writeTo(outputStream)

            result
        }

        DecompilationResult(file) = cached
      }

      cached
    case _ => DecompilationResult.empty()
  }

  private[this] def decompileInner(fileName: String, bytes: Array[Byte]) =
    try {
      Decompiler.decompile(fileName, bytes)
    } catch {
      case e: Exception =>
        LOG.warn(e.getMessage)
        None
    }
}
