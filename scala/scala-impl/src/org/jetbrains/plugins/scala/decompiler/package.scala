package org.jetbrains.plugins.scala

import java.io.IOException

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}

import scala.annotation.tailrec
import scala.reflect.NameTransformer.decode

package object decompiler {

  private[this] type SiblingsNames = Seq[String]

  private[this] val ClassFileExtension = "class"

  implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    def isInnerClass: Boolean = {
      def isClass(file: VirtualFile) =
        file.getExtension == ClassFileExtension

      !isClass(virtualFile) && {
        validSiblingsNames(isClass).map(decode) match {
          case Seq() => false
          case seq =>
            implicit val siblingsNames: SiblingsNames = seq
            virtualFile.getNameWithoutExtension match {
              case EndsWithDollar(name) if accepts(name) => false // let's handle it separately to avoid giving it for Java.
              case name => isInnerImpl(decode(name))
            }
        }
      }
    }

    def isAcceptable: Boolean = {
      def isScalaFile(file: VirtualFile) =
        try {
          file.decompile().isScala
        } catch {
          case _: IOException => false
        }

      isScalaFile(virtualFile) ||
        isAcceptableImpl("", virtualFile.getNameWithoutExtension) {
          validSiblingsNames(isScalaFile)
        }
    }

    def sourceContent: Option[String] =
      try {
        decompile() match {
          case DecompilationResult(true, _, sourceText) => Some(sourceText)
          case _ => None
        }
      } catch {
        case _: IOException => None
      }

    def sourceName: String = decompile().sourceName

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

    private def validSiblingsNames(predicate: VirtualFile => Boolean) =
      virtualFile.getParent match {
        case null => Seq.empty
        case parent => parent.getChildren.toSeq.filter(predicate).map(_.getNameWithoutExtension)
      }
  }

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private[this] def decompilerFileAttribute =
    if (ScalaLoader.isUnderUpsource) None
    else Some(ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute)

  private[this] object SplitAtDollar {

    def unapply(string: String): Option[(String, String)] = string.split("\\$", 2) match {
      case Array(prefix, suffix) => Some(prefix, suffix)
      case _ => None
    }
  }

  private[this] val EndsWithDollar = "(.+)\\$$".r

  @tailrec
  private[this] def isInnerImpl(suffix: String)
                               (implicit siblingsNames: SiblingsNames): Boolean = suffix match {
    case SplitAtDollar(newPrefix, newSuffix) =>
      accepts(newPrefix) || isInnerImpl(newSuffix)
    case _ => false
  }

  private[this] def accepts(newPrefix: String)
                           (implicit siblingsNames: SiblingsNames) =
    siblingsNames.exists(_ != newPrefix)

  @tailrec
  private[this] def isAcceptableImpl(prefix: String, suffix: String)
                                    (implicit siblingsNames: SiblingsNames): Boolean = suffix match {
    case SplitAtDollar(suffixPrefix, suffixSuffix) =>
      prefix + suffixPrefix match {
        case newPrefix if !newPrefix.endsWith("$") && siblingsNames.contains(newPrefix) => true
        case newPrefix => isAcceptableImpl(newPrefix + '$', suffixSuffix)
      }
    case _ => false
  }
}
