package scala.tools.scalap

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.lang.StringBuilder

import org.apache.bcel.classfile._

import scala.reflect.internal.pickling.ByteCodecs
import scala.tools.scalap.scalax.rules.scalasig.{ByteCode, ScalaSig, ScalaSigAttributeParsers, ScalaSigPrinter}

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

  private def isScalaSignatureAnnotation(entry: AnnotationEntry) = {
    val annType = entry.getAnnotationType
    annType == SCALA_SIG_ANNOTATION || annType == SCALA_LONG_SIG_ANNOTATION
  }

  private def toBytes(elemValue: ElementValue): Array[Byte] = {
    def simpleToBytes(sv: SimpleElementValue) = sv.getValueString.getBytes(StandardCharsets.UTF_8)

    elemValue match {
      case sv: SimpleElementValue => simpleToBytes(sv)
      case arr: ArrayElementValue =>
        val fromSimpleValues = arr.getElementValuesArray.collect {
          case sv: SimpleElementValue => simpleToBytes(sv)
        }
        Array.concat(fromSimpleValues: _*)
    }
  }

  private def parseScalaSig(entry: AnnotationEntry) = {
    val bytesValue = entry.getElementValuePairs.find(_.getNameString == BYTES_VALUE)
    bytesValue match {
      case Some(v) =>
        val bytes = toBytes(v.getValue)
        val length = ByteCodecs.decode(bytes)
        Some(ScalaSigAttributeParsers.parse(ByteCode(bytes.take(length))))
      case _ => None
    }
  }

  def decompile(fileName: String, bytes: Array[Byte]): Option[(String, String)] = {
    if (!hasScalaSigBytes(bytes)) return None

    val parsed = new ClassParser(new ByteArrayInputStream(bytes), fileName).parse()

    val scalaSig =
      parsed.getAnnotationEntries
        .find(isScalaSignatureAnnotation)
        .flatMap(parseScalaSig)

    scalaSig.map { sig =>
      val decompiledSourceText = decompiledText(fileName, sig)
      val sourceFileName = parsed.getSourceFileName
      (sourceFileName, decompiledSourceText)
    }
  }

  private def decompiledText(fileName: String, scalaSig: ScalaSig) = {
    val printer = new ScalaSigPrinter(new StringBuilder, false)

    val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
    // Print package with special treatment for package objects
    syms.head.parent match {
      //Partial match
      case Some(p) if p.name != "<empty>" =>
        val path = p.path
        val isPackageObject = fileName == "package.class"

        if (!isPackageObject) {
          printer.print("package ")
          printer.print(ScalaSigPrinter.processName(path))
          printer.print("\n")
        } else {
          val i = path.lastIndexOf(".")
          if (i > 0) {
            printer.print("package ")
            printer.print(ScalaSigPrinter.processName(path.substring(0, i)))
            printer.print("\n")
          }
        }
      case _ =>
    }

    // Print classes
    for (c <- syms) {
      printer.printSymbol(c)
    }

    printer.result
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
