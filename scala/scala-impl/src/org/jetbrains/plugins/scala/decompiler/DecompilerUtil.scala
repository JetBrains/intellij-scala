package org.jetbrains.plugins.scala
package decompiler

import java.io._

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.reference.SoftReference

/**
 * @author ilyas
 */
object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil")

  val DECOMPILER_VERSION = 289
  private val SCALA_DECOMPILER_FILE_ATTRIBUTE = new FileAttribute("_is_scala_compiled_new_key_", DECOMPILER_VERSION, true)
  private val SCALA_DECOMPILER_KEY = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

  class DecompilationResult(val isScala: Boolean, val sourceName: String, val timeStamp: Long) {
    def sourceText: String = ""
  }
  object DecompilationResult {
    def empty: DecompilationResult = new DecompilationResult(isScala = false, sourceName = "", timeStamp = 0L)
  }

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private def attributesSupported = !ScalaLoader.isUnderUpsource

  def isScalaFile(file: VirtualFile): Boolean =
    try isScalaFile(file, file.contentsToByteArray)
    catch {
      case _: IOException => false
    }
  def isScalaFile(file: VirtualFile, bytes: => Array[Byte]): Boolean = decompile(file, bytes).isScala
  def decompile(file: VirtualFile, bytes: => Array[Byte]): DecompilationResult = {
    if (!file.isInstanceOf[VirtualFileWithId]) return DecompilationResult.empty
    val timeStamp = file.getTimeStamp
    var data = file.getUserData(SCALA_DECOMPILER_KEY)
    var res: DecompilationResult = if (data == null) null else data.get()
    if (res == null || res.timeStamp != timeStamp) {
      val readAttribute = if (attributesSupported) SCALA_DECOMPILER_FILE_ATTRIBUTE.readAttribute(file) else null
      def updateAttributeAndData() {
        val decompilationResult = decompileInner(file, bytes)
        if (attributesSupported) {
          val writeAttribute = SCALA_DECOMPILER_FILE_ATTRIBUTE.writeAttribute(file)
          try {
            writeAttribute.writeBoolean(decompilationResult.isScala)
            writeAttribute.writeUTF(decompilationResult.sourceName)
            writeAttribute.writeLong(decompilationResult.timeStamp)
            writeAttribute.close()
          } catch {
            case _: IOException =>
          }
        }
        res = decompilationResult
      }
      if (readAttribute != null) {
        try {
          val isScala = readAttribute.readBoolean()
          val sourceName = readAttribute.readUTF()
          val attributeTimeStamp = readAttribute.readLong()
          if (attributeTimeStamp != timeStamp) updateAttributeAndData()
          else res = new DecompilationResult(isScala, sourceName, attributeTimeStamp) {
            override lazy val sourceText: String = {
              decompileInner(file, bytes).sourceText
            }
          }
        }
        catch {
          case _: IOException => updateAttributeAndData()
        }
      } else updateAttributeAndData()
      data = new SoftReference[DecompilationResult](res)
      file.putUserData(SCALA_DECOMPILER_KEY, data)
    }
    res
  }

  private def decompileInner(file: VirtualFile, bytes: Array[Byte]): DecompilationResult = {
    val result =
      try Decompiler.decompile(file.getName, bytes)
      catch {
        case e: Exception =>
          LOG.warn(e.getMessage)
          None
      }

    result match {
      case Some((sourceFileName, decompiledSourceText)) =>
        new DecompilationResult(isScala = true, sourceFileName, file.getTimeStamp) {
          override def sourceText: String = decompiledSourceText
        }
      case _ => new DecompilationResult(isScala = false, "", file.getTimeStamp)
    }
  }

  object Opcodes {
    val iconst_0 = 0x03.toByte

    val istore_0 = 0x3b.toByte
    val istore_1 = 0x3c.toByte
    val istore_2 = 0x3d.toByte
    val istore_3 = 0x3e.toByte
    val istore   = 0x36.toByte

    val iload_0  = 0x1a.toByte
    val iload_1  = 0x1b.toByte
    val iload_2  = 0x1c.toByte
    val iload_3  = 0x1d.toByte
    val iload    = 0x15.toByte

    val aload    = 0x19.toByte
    val aload_0  = 0x2a.toByte
    val aload_1  = 0x2b.toByte
    val aload_2  = 0x2c.toByte
    val aload_3  = 0x2d.toByte

    val astore   = 0x3a.toByte
    val astore_0 = 0x4b.toByte
    val astore_1 = 0x4c.toByte
    val astore_2 = 0x4d.toByte
    val astore_3 = 0x4e.toByte

    val dload    = 0x18.toByte
    val dload_0  = 0x26.toByte
    val dload_1  = 0x27.toByte
    val dload_2  = 0x28.toByte
    val dload_3  = 0x29.toByte

    val dstore   = 0x39.toByte
    val dstore_0 = 0x47.toByte
    val dstore_1 = 0x48.toByte
    val dstore_2 = 0x49.toByte
    val dstore_3 = 0x4a.toByte

    val fload    = 0x17.toByte
    val fload_0  = 0x22.toByte
    val fload_1  = 0x23.toByte
    val fload_2  = 0x24.toByte
    val fload_3  = 0x25.toByte

    val fstore   = 0x38.toByte
    val fstore_0 = 0x43.toByte
    val fstore_1 = 0x44.toByte
    val fstore_2 = 0x45.toByte
    val fstore_3 = 0x46.toByte

    val lload    = 0x16.toByte
    val lload_0  = 0x1e.toByte
    val lload_1  = 0x1f.toByte
    val lload_2  = 0x20.toByte
    val lload_3  = 0x21.toByte

    val lstore   = 0x37.toByte
    val lstore_0 = 0x3f.toByte
    val lstore_1 = 0x40.toByte
    val lstore_2 = 0x41.toByte
    val lstore_3 = 0x42.toByte

    val invokeStatic = 0xB8.toByte

    val areturn = 0xB0.toByte
    val dreturn = 0xAF.toByte
    val freturn = 0xAE.toByte
    val ireturn = 0xAC.toByte
    val lreturn = 0xAD.toByte
    val voidReturn = 0xB1.toByte

    val goto = 0xA7.toByte
  }
}
