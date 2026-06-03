package org.jetbrains.plugins.scala.lang.psi.compiled

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId, newvfs}
import org.jetbrains.plugins.scala.decompiler.Decompiler
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClassFileDecompiler.ScClsStubBuilder.getStubVersion
import org.jetbrains.plugins.scala.tasty.reader.CompilerOptions
import org.jetbrains.plugins.scala.tasty.{TastyFileType, TastyReader}

import java.io.{DataInputStream, DataOutputStream, IOException}
import java.lang.ref.SoftReference
import scala.util.control.NonFatal

private sealed trait DecompilationResult {
  val isScala: Boolean
  val sourceName: String
  def compilerOptions: CompilerOptions
  val timeStamp: Long
}

private sealed trait ScalaDecompilationResult extends DecompilationResult {
  override val isScala = true
  def sourceText: String
}

private object DecompilationResult {

  private sealed trait WritableResult extends DecompilationResult {
    def writeTo(outputStream: DataOutputStream): Unit = {
      outputStream.writeBoolean(isScala)
      outputStream.writeUTF(sourceName)
      outputStream.writeLong(timeStamp)
      outputStream.writeBoolean(compilerOptions.kindProjector)
    }
  }

  private case class NonScala(override val timeStamp: Long) extends WritableResult {
    override val isScala: Boolean = false
    override val sourceName: String = ""
    override def compilerOptions: CompilerOptions = CompilerOptions.Default
  }

  private case class PartialScala(override val sourceName: String, compilerOptions: CompilerOptions, override val timeStamp: Long) extends WritableResult {
    override val isScala: Boolean = true
  }

  private case class Full(override val sourceName: String, override val compilerOptions: CompilerOptions, override val sourceText: String, override val timeStamp: Long) extends ScalaDecompilationResult

  private case class Lazy(override val sourceName: String, override val compilerOptions: CompilerOptions, override val timeStamp: Long, sourceTextComputation: () => String) extends ScalaDecompilationResult {
    override lazy val sourceText: String = sourceTextComputation()
  }

  private def toWritable(decompilationResult: DecompilationResult): WritableResult = decompilationResult match {
    case result: ScalaDecompilationResult => PartialScala(result.sourceName, result.compilerOptions, result.timeStamp)
    case _                                => NonScala(decompilationResult.timeStamp)
  }

  private val Key = new Key[SoftReference[DecompilationResult]]("Is Scala File Key")

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private[compiled] val DecompilerFileAttribute: Option[FileAttribute] =
    if (ScalaCompilerLoader.isDisabled) None
    else Some(new newvfs.FileAttribute("_is_scala_compiled_new_key_", getStubVersion, true))

  private def getFromUserData(file: VirtualFile): DecompilationResult =
    file.getUserData(Key) match {
      case null => null
      case data => data.get()
    }

  private def cacheInUserData(file: VirtualFile, result: DecompilationResult): Unit = {
    file.putUserData(Key, new SoftReference(result))
  }

  private[compiled] def sourceNameAndText(file: VirtualFile, bytes: Array[Byte] = null): Option[(String, String)] =
    tryDecompile(file, bytes).map { result =>
      (result.sourceName, result.sourceText)
    }

  private[compiled] def tryDecompile(file: VirtualFile, bytes: Array[Byte] = null): Option[ScalaDecompilationResult] = {
    val maybeContent: Option[() => Array[Byte]] = bytes match {
      case null =>
        if (file.isDirectory || !file.isInstanceOf[VirtualFileWithId])
          None
        else
          Some(() => file.contentsToByteArray)
      case content =>
        Some(() => content)
    }
    try {
      maybeContent.flatMap(decompile(file, _))
    }
    catch {
      case _: IOException =>
        None
    }
  }

  private def decompile(file: VirtualFile, content: () => Array[Byte]): Option[ScalaDecompilationResult] = {
    val timeStamp = file.getTimeStamp

    val fromCache = getFromUserData(file)
    if (fromCache != null && fromCache.timeStamp == timeStamp)
      return fromCache.asOptionOf[ScalaDecompilationResult]

    val cachedFromFileAttributes = getFromFileAttribute(file)
    val result: DecompilationResult = cachedFromFileAttributes match {
      case Some(nonScala: NonScala) =>
        nonScala
      case Some(PartialScala(sourceName, compilerOptions, _)) =>
        Lazy(sourceName, compilerOptions, timeStamp, () => sourceNameAndText(file, content).map(_._2).getOrElse(""))
      case None =>
        val recomputedResult = sourceNameAndText(file, content) match {
          case Some((sourceName, sourceText, compilerOptions)) => Full(sourceName, compilerOptions, sourceText, timeStamp)
          case None                           => NonScala(timeStamp)
        }

        // Save to file attribute as a "cache"
        writeToFileAttribute(file, recomputedResult)

        recomputedResult

    }
    cacheInUserData(file, result)

    result.asOptionOf[ScalaDecompilationResult]
  }

  private def sourceNameAndText(file: VirtualFile, content: () => Array[Byte]): Option[(String, String, CompilerOptions)] = {
    if (file.getExtension == TastyFileType.getDefaultExtension) {
      try {
        TastyReader.read(content.apply())
      } catch {
        case NonFatal(e) => throw new RuntimeException("Error parsing " + file.getPath, e)
      }
    } else {
      Decompiler.sourceNameAndText(file.getName, content()).map(p => (p._1, p._2, CompilerOptions.Default))
    }
  }

  private def getFromFileAttribute(file: VirtualFile): Option[DecompilationResult.WritableResult] = {
    for {
      attribute <- DecompilerFileAttribute
      readAttribute <- Option(attribute.readFileAttribute(file))

      result <- readFrom(readAttribute)
      if result.timeStamp == file.getTimeStamp
    } yield result
  }

  private[this] def readFrom(inputStream: DataInputStream): Option[DecompilationResult.WritableResult] = try {
    val isScala = inputStream.readBoolean()
    val sourceName = inputStream.readUTF()
    val timeStamp = inputStream.readLong()
    val compilerOptions = CompilerOptions(kindProjector = inputStream.readBoolean())
    Some {
      if (isScala) PartialScala(sourceName, compilerOptions, timeStamp)
      else NonScala(timeStamp)
    }
  } catch {
    case _: IOException => None
  }

  private def writeToFileAttribute(file: VirtualFile, result: DecompilationResult): Unit = {
    for {
      attribute <- DecompilerFileAttribute
      outputStream = attribute.writeFileAttribute(file)
    } {
      try {
        toWritable(result).writeTo(outputStream)
        outputStream.close()
      } catch {
        case _: IOException =>
      }
    }
  }
}
