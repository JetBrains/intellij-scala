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

  val DECOMPILER_VERSION = 306
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
}
