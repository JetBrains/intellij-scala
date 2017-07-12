package scala.tools.scalap

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

import scala.reflect.internal.pickling.ByteCodecs
import scala.tools.scalap.scalax.rules.scalasig.ClassFileParser.{Annotation, ArrayValue, ConstValueIndex}
import scala.tools.scalap.scalax.rules.scalasig._

/**
 * @author Alefas
 * @since  11/09/15
 */
object Decompiler {
  private val UTF8 = "UTF-8"
  private val SOURCE_FILE = "SourceFile"
  private val SCALA_SIG = "ScalaSig"
  private val SCALA_SIG_ANNOTATION = "Lscala/reflect/ScalaSignature;"
  private val SCALA_LONG_SIG_ANNOTATION = "Lscala/reflect/ScalaLongSignature;"
  private val BYTES_VALUE = "bytes"

  private val scalaSigBytes = SCALA_SIG.getBytes(StandardCharsets.UTF_8)

  private def hasScalaSigBytes(content: Array[Byte]): Boolean = containsSubArray(content, scalaSigBytes)

  def decompile(fileName: String, bytes: Array[Byte]): Option[(String, String)] = {
    if (!hasScalaSigBytes(bytes)) return None

    val byteCode = ByteCode(bytes)
    val isPackageObject = fileName == "package.class"
    val classFile = ClassFileParser.parse(byteCode)
    val scalaSig = classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
      // No entries in ScalaSig attribute implies that the signature is stored in the annotation
      case Some(ScalaSig(_, _, entries)) if entries.isEmpty =>
        import classFile._
        val annotation = classFile.annotation(SCALA_SIG_ANNOTATION)
          .orElse(classFile.annotation(SCALA_LONG_SIG_ANNOTATION))
        annotation match {
          case None => null
          case Some(Annotation(_, elements)) =>
            val bytesElem = elements.find(elem => constant(elem.elementNameIndex) == BYTES_VALUE).get

            val parts = (bytesElem.elementValue match {
              case ConstValueIndex(index) => Seq(constantWrapped(index))
              case ArrayValue(seq) => seq.collect {case ConstValueIndex(index) => constantWrapped(index)}
            }).collect {case x: StringBytesPair => x.bytes}

            val bytes = parts.reduceLeft(Array.concat(_, _))

            val length = ByteCodecs.decode(bytes)
            val scalaSig = ScalaSigAttributeParsers.parse(ByteCode(bytes.take(length)))
            scalaSig
        }
      case Some(other) => other
      case None => null
    }
    if (scalaSig == null) return None
    val decompiledSourceText = {
      val baos = new ByteArrayOutputStream
      val stream = new PrintStream(baos, true, UTF8)
      if (scalaSig == null) return None
      val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
      // Print package with special treatment for package objects
      syms.head.parent match {
        //Partial match
        case Some(p) if p.name != "<empty>" =>
          val path = p.path
          if (!isPackageObject) {
            stream.print("package ")
            stream.print(ScalaSigPrinter.processName(path))
            stream.print("\n")
          } else {
            val i = path.lastIndexOf(".")
            if (i > 0) {
              stream.print("package ")
              stream.print(ScalaSigPrinter.processName(path.substring(0, i)))
              stream.print("\n")
            }
          }
        case _ =>
      }

      // Print classes
      val printer = new ScalaSigPrinter(stream, false)

      for (c <- syms) {
        printer.printSymbol(c)
      }
      val sourceBytes = baos.toByteArray
      new String(sourceBytes, UTF8)
    }

    val sourceFileName = {
      classFile.attribute(SOURCE_FILE) match {
        case Some(attr: Attribute) =>
          val SourceFileInfo(index: Int) = SourceFileAttributeParser.parse(attr.byteCode)
          val c = classFile.header.constants(index)
          val sBytes: Array[Byte] = c match {
            case s: String => s.getBytes(UTF8)
            case scala.tools.scalap.scalax.rules.scalasig.StringBytesPair(_: String, bytes: Array[Byte]) => bytes
            case _ => Array.empty
          }
          new String(sBytes, UTF8)
        case None => "-no-source-"
      }
    }
    Some(sourceFileName, decompiledSourceText)
  }

  private def containsSubArray(text: Array[Byte], word: Array[Byte]): Boolean = {
    if (text.length < word.length || word.length == 0) return false

    var wordStartIdx = 0
    var innerIdx = 0

    while (wordStartIdx <= text.length - word.length) {
      while(innerIdx < word.length && text(wordStartIdx + innerIdx) == word(innerIdx)) {
        innerIdx += 1
      }
      if (innerIdx == word.length) return true
      else {
        wordStartIdx += 1
        innerIdx = 0
      }
    }
    false
  }

}
