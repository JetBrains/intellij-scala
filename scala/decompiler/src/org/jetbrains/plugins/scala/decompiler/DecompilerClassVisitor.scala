package org.jetbrains.plugins.scala.decompiler

import org.jetbrains.org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}

import java.nio.charset.StandardCharsets

private final class DecompilerClassVisitor extends ClassVisitor(Opcodes.ASM9) {
  import Decompiler.{BYTES_VALUE, SCALA_LONG_SIG_ANNOTATION, SCALA_SIG_ANNOTATION}

  /*
   * Contains the values of ScalaSignature and ScalaLongSignature class annotations. In the case of ScalaSignature,
   * there is only 1 encoded value. In the case of ScalaLongSignature, there are multiple encoded values in an array.
   */
  private val rawSignature: java.lang.StringBuilder = new java.lang.StringBuilder()

  private[this] var _source: Option[String] = None

  def signature: Option[Array[Byte]] =
    Option(rawSignature.toString)
      .filter(_.nonEmpty)
      .map(_.getBytes(StandardCharsets.UTF_8))

  def source: Option[String] = _source

  override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = descriptor match {
    case SCALA_SIG_ANNOTATION | SCALA_LONG_SIG_ANNOTATION =>
      new AnnotationVisitor(Opcodes.ASM9) {
        override def visit(name: String, value: Any): Unit = {
          if (name == BYTES_VALUE) {
          // We are visiting the ScalaSignature annotation that only contains one encoded value as a string named "bytes"
            rawSignature.append(value.toString)
          }
        }

        override def visitArray(name: String): AnnotationVisitor = {
          if (name == BYTES_VALUE) {
            // We are visiting the ScalaLongSignature annotation that contains multiple encoded values as a string array named "bytes"
            new AnnotationVisitor(Opcodes.ASM9) {
              override def visit(name: String, value: Any): Unit = {
                rawSignature.append(value.toString)
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
