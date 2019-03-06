package org.jetbrains.plugins.scala
package lang
package psi

import java.io.IOException

import com.intellij.openapi.vfs.VirtualFile

import scala.annotation.tailrec
import scala.reflect.NameTransformer.decode

package object compiled {

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
      def isScalaFile(file: VirtualFile) = try {
        DecompilationResult.decompile(file).isScala
      } catch {
        case _: IOException => false
      }

      isScalaFile(virtualFile) ||
        isAcceptableImpl("", virtualFile.getNameWithoutExtension) {
          validSiblingsNames(isScalaFile)
        }
    }

    private def validSiblingsNames(predicate: VirtualFile => Boolean) =
      virtualFile.getParent match {
        case null => Seq.empty
        case parent => parent.getChildren.toSeq.filter(predicate).map(_.getNameWithoutExtension)
      }
  }

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
