package org.jetbrains.plugins.scala.decompiler

import org.jetbrains.org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}
import org.jetbrains.plugins.scala.decompiler.scalasig.{Parser, ScalaSig}

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.reflect.internal.pickling.ByteCodecs

private final class DecompilerClassVisitor(fileName: String) extends ClassVisitor(Opcodes.ASM9) {
  import Decompiler.{BYTES_VALUE, SCALA_SIG_ANNOTATION, SCALA_LONG_SIG_ANNOTATION}

  /*
   * Contains the values of ScalaSignature and ScalaLongSignature class annotations. In the case of ScalaSignature,
   * there is only 1 encoded value. In the case of ScalaLongSignature, there are multiple encoded values in an array.
   */
  private val rawSignature: mutable.ListBuffer[String] = mutable.ListBuffer.empty

  /*
   * The string `"<Unknown>"` as a default value for the source file name of a given classfile is taken from
   * https://github.com/apache/commons-bcel/blob/29175909fb8c039613e46d071fdbbe5bf7914f49/src/main/java/org/apache/bcel/classfile/JavaClass.java#L118.
   * The Apache BCEL bytecode manipulation library was previously used to implement our decompiler. The default
   * value is kept for reasons of maintaining the behavior of the decompiler, in case a source file name cannot be
   * decoded from the classfile.
   */
  private[this] var _source: String = "<Unknown>"

  def signature: Option[ScalaSig] = {
    val chunks = rawSignature.result().map(_.getBytes(StandardCharsets.UTF_8))

    val bytes = chunks match {
      case Nil => None
      case List(signature) =>
        // Do not create a copy of the bytes, if there is only one byte array.
        Some(signature)
      case signatures =>
        Some(Array.concat(signatures: _*))
    }

    bytes.map { bs =>
      ByteCodecs.decode(bs)
      Parser.parseScalaSig(bs, fileName)
    }
  }

  def source: String = _source

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
    _source = source
  }
}
