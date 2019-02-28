package org.jetbrains.plugins.scala

import java.io.IOException

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}

import scala.annotation.tailrec

package object decompiler {

  implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    import reflect.NameTransformer.decode

    def isInnerClass: Boolean =
      virtualFile.getParent match {
        case null => false
        case parent => !isClass && parent.isInner(virtualFile.getNameWithoutExtension)
      }

    def isInner(name: String): Boolean = {
      if (name.endsWith("$") && contains(name, name.length - 1)) {
        return false //let's handle it separately to avoid giving it for Java.
      }
      isInner(decode(name), 0)
    }

    private def isInner(name: String, from: Int): Boolean = {
      val index = name.indexOf('$', from)

      val containsPart = index > 0 && contains(name, index)
      index != -1 && (containsPart || isInner(name, index + 1))
    }

    private def contains(name: String, endIndex: Int): Boolean =
      virtualFile.getChildren.exists { child =>
        child.isClass &&
          decode(child.getNameWithoutExtension) != name.substring(0, endIndex)
      }

    def isClass: Boolean = virtualFile.getExtension == "class"

    def canBeProcessed: Boolean = {
      val maybeParent = Option(virtualFile.getParent)

      @tailrec
      def go(prefix: String, suffix: String): Boolean = {
        if (!prefix.endsWith("$")) {
          if (maybeParent
            .map(_.findChild(prefix + ".class"))
            .exists(_.isScalaFile)) return true
        }

        split(suffix) match {
          case Some((suffixPrefix, suffixSuffix)) => go(prefix + "$" + suffixPrefix, suffixSuffix)
          case _ => false
        }
      }

      split(virtualFile.getNameWithoutExtension) match {
        case Some((prefix, suffix)) => go(prefix, suffix)
        case _ => false
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

    def isScalaFile: Boolean =
      try {
        decompile().isScala
      } catch {
        case _: IOException => false
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

  // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
  private[this] def decompilerFileAttribute =
    if (ScalaLoader.isUnderUpsource) None
    else Some(ScClassFileDecompiler.ScClsStubBuilder.DecompilerFileAttribute)

  private[this] def split(string: String) = string.indexOf('$') match {
    case -1 => None
    case index => Some(string.substring(0, index), string.substring(index + 1))
  }
}
