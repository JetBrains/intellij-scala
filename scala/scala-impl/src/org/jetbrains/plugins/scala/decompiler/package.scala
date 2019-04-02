package org.jetbrains.plugins.scala

import java.io.IOException

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}

import scala.reflect.NameTransformer

package object decompiler {

  private val ClassFileExtension = JavaClassFileType.INSTANCE.getDefaultExtension

  private def classFileName(withoutExtension: String) = s"$withoutExtension.$ClassFileExtension"

  implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    private def topLevelScalaClass: Option[String] = {
      val parent = virtualFile.getParent

      if (parent == null || virtualFile.getExtension != ClassFileExtension)
        return None

      val prefixIterator = new PrefixIterator(virtualFile.getNameWithoutExtension)

      prefixIterator.filterNot(_.endsWith("$"))
        .find { prefix =>
          val candidate = parent.findChild(classFileName(prefix))
          candidate != null && canBeDecompiled(candidate)
        }
    }

    def isAcceptable: Boolean = topLevelScalaClass.nonEmpty

    def isInnerClass: Boolean = {
      val fileName = virtualFile.getNameWithoutExtension
      !topLevelScalaClass.contains(fileName)
    }

    def decompile(bytes: => Array[Byte] = virtualFile.contentsToByteArray): DecompilationResult = virtualFile match {
      case _: VirtualFileWithId =>
        implicit val timeStamp: Long = virtualFile.getTimeStamp
        var cached = DecompilationResult(virtualFile)

        if (cached == null || cached.timeStamp != timeStamp) {
          val maybeResult = for {
            attribute <- decompilerFileAttribute
            readAttribute <- Option(attribute.readAttribute(virtualFile))

            result <- DecompilationResult.readFrom(readAttribute)
            if result.timeStamp == timeStamp
          } yield result

          val fileName = virtualFile.getName
          cached = maybeResult match {
            case Some(result) =>
              new DecompilationResult(result.isScala, result.sourceName) {
                override protected lazy val rawSourceText: String =
                  Decompiler(fileName, bytes).fold("")(_._2)
              }
            case _ =>
              val result = Decompiler(fileName, bytes)
                .fold(DecompilationResult.empty) {
                  case (sourceFileName, decompiledSourceText) =>
                    new DecompilationResult(isScala = true, sourceFileName) {
                      override protected def rawSourceText: String = decompiledSourceText
                    }
                }

              for {
                attribute <- decompilerFileAttribute
                outputStream = attribute.writeAttribute(virtualFile)
              } result.writeTo(outputStream)

              result
          }

          DecompilationResult(virtualFile) = cached
        }

        cached
      case _ => DecompilationResult.empty()
    }

  }

  private def canBeDecompiled(file: VirtualFile): Boolean =
    try {
      file.decompile().isScala
    } catch {
      case _: IOException => false
    }

  private class PrefixIterator(_fullName: String) extends Iterator[String] {
    //we need to decode first to avoid treating class `:::` as inner of class `::` and so on
    private val decoded = NameTransformer.decode(_fullName)
    private var currentPrefixEnd = -1

    def hasNext: Boolean = currentPrefixEnd < decoded.length

    def next(): String = {
      val prefix = nextPrefix(currentPrefixEnd)
      currentPrefixEnd = prefix.length
      NameTransformer.encode(prefix)
    }

    private def nextPrefix(previousPrefixEnd: Int = -1): String = {
      if (previousPrefixEnd == decoded.length) null
      else {
        val nextDollarIndex = decoded.indexOf('$', previousPrefixEnd + 1)

        if (nextDollarIndex >= 0) decoded.substring(0, nextDollarIndex)
        else decoded
      }
    }
  }

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private[this] def decompilerFileAttribute =
    if (ScalaLoader.isUnderUpsource) None
    else Some (ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute)
}
