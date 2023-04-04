package org.jetbrains.plugins.scala.decompiler

import org.jetbrains.org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}

import java.nio.charset.StandardCharsets
import scala.collection.mutable

private final class DecompilerClassVisitor extends ClassVisitor(Opcodes.ASM9) {
  import Decompiler.{BYTES_VALUE, SCALA_LONG_SIG_ANNOTATION, SCALA_SIG_ANNOTATION}

  /*
   * Contains the values of ScalaSignature and ScalaLongSignature class annotations. In the case of ScalaSignature,
   * there is only 1 encoded value. In the case of ScalaLongSignature, there are multiple encoded values in an array.
   */
  private val rawSignature: mutable.ListBuffer[String] = mutable.ListBuffer.empty

  private[this] var _source: Option[String] = None

  def signature: Option[Array[Byte]] = {
    val chunks = rawSignature.result().map(_.getBytes(StandardCharsets.UTF_8))

    chunks match {
      case Nil => None
      case List(signature) =>
        // Do not create a copy of the bytes, if there is only one byte array.
        Some(signature)
      case signatures =>
        Some(Array.concat(signatures: _*))
    }
  }

  def source: Option[String] = _source

  override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = descriptor match {
    case SCALA_SIG_ANNOTATION | SCALA_LONG_SIG_ANNOTATION =>
      new AnnotationVisitor(Opcodes.ASM9) {
        override def visit(name: String, value: Any): Unit = {
          if (name == BYTES_VALUE) {
          // We are visiting the ScalaSignature annotation that only contains one encoded value as a string named "bytes"
            rawSignature += value.toString
          }
        }

        override def visitArray(name: String): AnnotationVisitor = {
          if (name == BYTES_VALUE) {
            // We are visiting the ScalaLongSignature annotation that contains multiple encoded values as a string array named "bytes"
            new AnnotationVisitor(Opcodes.ASM9) {
              override def visit(name: String, value: Any): Unit = {
                rawSignature += value.toString
              }
            }
          } else null
        }
      }

    case _ => null
  }

  override def visitSource(source: String, debug: String): Unit = {
    _source = Option(source)
  }
}
