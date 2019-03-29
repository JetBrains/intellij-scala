package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile

import scala.annotation.tailrec
import scala.reflect.NameTransformer.decode

package object compiled {

  implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    def isInnerClass: Boolean = !isClass(virtualFile) && withSiblings { siblings =>
      val predicate = siblings match {
        case Seq((_, fileName)) =>
          val decodedName = Set(decode(fileName))
          (decodedName(_)).andThen(!_)
        case _ => Function.const(true)(_: String)
      }

      virtualFile.getNameWithoutExtension match {
        case EndsWithDollar(name) if predicate(name) => false // let's handle it separately to avoid giving it for Java.
        case name => isInnerImpl(decode(name))(predicate)
      }
    }

    def isAcceptable: Boolean = isScalaFile(virtualFile) || withSiblings { siblings =>
      isAcceptableImpl("", virtualFile.getNameWithoutExtension) { prefix =>
        siblings.exists {
          case (file, fileName) => Comparing.equal(fileName, prefix, true) && isScalaFile(file)
        }
      }
    }

    private def withSiblings(predicate: Seq[(VirtualFile, String)] => Boolean) = virtualFile.getParent match {
      case null => false
      case parent =>
        val siblings = for {
          sibling <- parent.getChildren
          if isClass(sibling)
        } yield (sibling, sibling.getNameWithoutExtension)

        siblings.nonEmpty && predicate(siblings)
    }
  }

  private[this] def isClass(file: VirtualFile): Boolean =
    file.getExtension == "class"

  private[this] def isScalaFile(file: VirtualFile): Boolean =
    DecompilationResult.tryDecompile(file).isDefined

  private[this] object SplitAtDollar {

    def unapply(string: String): Option[(String, String)] = string.split("\\$", 2) match {
      case Array(prefix, suffix) => Some(prefix, suffix)
      case _ => None
    }
  }

  private[this] val EndsWithDollar = "(.+)\\$$".r

  @tailrec
  private[this] def isInnerImpl(suffix: String)
                               (implicit predicate: String => Boolean): Boolean = suffix match {
    case SplitAtDollar(newPrefix, newSuffix) =>
      predicate(newPrefix) || isInnerImpl(newSuffix)
    case _ => false
  }

  @tailrec
  private[this] def isAcceptableImpl(prefix: String, suffix: String)
                                    (implicit predicate: String => Boolean): Boolean = suffix match {
    case SplitAtDollar(suffixPrefix, suffixSuffix) =>
      prefix + suffixPrefix match {
        case newPrefix if !newPrefix.endsWith("$") && predicate(newPrefix) => true
        case newPrefix => isAcceptableImpl(newPrefix + '$', suffixSuffix)
      }
    case _ => false
  }
}
