package org.jetbrains.plugins.scala
package decompiler

import java.io.ByteArrayInputStream
import java.lang.StringBuilder
import java.nio.charset.StandardCharsets.UTF_8

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import org.apache.bcel.classfile._

import scala.reflect.internal.pickling.ByteCodecs

object Decompiler {
  val BYTES_VALUE                       = "bytes"
  val SCALA_SIG_ANNOTATION: String      = "Lscala/reflect/ScalaSignature;"
  val SCALA_LONG_SIG_ANNOTATION: String = "Lscala/reflect/ScalaLongSignature;"

  import scalasig._

  private[this] val Log: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.decompiler.DecompilerUtil")

  private val ScalaSigBytes = "ScalaSig".getBytes(UTF_8)

  def sourceNameAndText(fileName: String, bytes: Array[Byte]): Option[(String, String)] = {

    if (fileName.endsWith(".sig")) {
      return tryDecompileSigFile(fileName, bytes)
    }

    if (!containsMarker(bytes)) return None

    val parsed = new ClassParser(new ByteArrayInputStream(bytes), fileName).parse()
    for {
      annotation <- parsed.getAnnotationEntries.find(isScalaSignatureAnnotation)
      pair <- annotation.getElementValuePairs.find(_.getNameString == "bytes")

      simpleValues = collectSimple(pair.getValue)
      strings = simpleValues.map(valueBytes)

      bytes = decode(strings)
      signature = Parser.parseScalaSig(bytes, fileName)

      text <- decompiledText(signature, parsed.getClassName, fileName == "package.class")
    } yield (parsed.getSourceFileName, StringUtil.convertLineSeparators(text))
  }

  private def tryDecompileSigFile(fileName: String, bytes: Array[Byte]): Option[(String, String)] = {
    try {
      val scalaSig = Parser.parseScalaSig(bytes, fileName)
      val isPackageObject = fileName == "package.sig"
      val sourceNameGuess = fileName.stripSuffix(".sig") + ".scala"

      decompiledText(scalaSig, fileName, isPackageObject)
        .map((sourceNameGuess, _))
    } catch {
      case e: Exception =>
        //not every `.sig` file is a scala signature file
        Log.debug(s"Couldn't decompile scala signatures from file $fileName", e)
        None
    }
  }

  private def isScalaSignatureAnnotation(entry: AnnotationEntry) =
    entry.getAnnotationType match {
      case "Lscala/reflect/ScalaSignature;" |
           "Lscala/reflect/ScalaLongSignature;" => true
      case _ => false
    }

  private def collectSimple(value: ElementValue) = value match {
    case simpleValue: SimpleElementValue => simpleValue :: Nil
    case arrayValue: ArrayElementValue =>
      arrayValue.getElementValuesArray.collect {
        case simpleValue: SimpleElementValue => simpleValue
      }.toList
  }

  private def valueBytes(value: SimpleElementValue) =
    value.getValueString.getBytes(UTF_8)

  private def decode(strings: List[Array[Byte]]) = {
    val bytes = Array.concat(strings: _*)
    ByteCodecs.decode(bytes)
    bytes
  }

  private def decompiledText(scalaSig: ScalaSig,
                             className: String,
                             isPackageObject: Boolean) =
    try {
      val builder = new StringBuilder

      val printer = new ScalaSigPrinter(builder)

      def findPath(symbol: Symbol) = symbol.name match {
        case "<empty>" => None
        case _ =>
          val path = symbol.path
          if (isPackageObject) {
            path.lastIndexOf(".") match {
              case -1 | 0 => None
              case index => Some(path.substring(0, index))
            }
          } else Some(path)
      }

      val symbols = scalaSig.topLevelClasses ++ scalaSig.topLevelObjects
      // Print package with special treatment for package objects

      for {
        symbol <- symbols.headOption
        parent <- symbol.parent
        path <- findPath(parent)
        packageName = ScalaSigPrinter.processName(path)
      } {
        printer.print("package ")
        printer.print(packageName)
        printer.print("\n\n")
      }

      val previousLength = builder.length

      // Print classes
      for (symbol <- symbols) {
        val previousLenght = builder.length
        printer.printSymbol(symbol)
        if (builder.length > previousLenght) builder.append("\n")
      }

      if (builder.length > previousLength) {
        builder.delete(builder.length - 1, builder.length)
      }

      if (!builder.isEmpty && builder.charAt(builder.length - 1) == '\n') {
        builder.setLength(builder.length - 1)
      }

      Some(printer.result)
    } catch {
      case e: ScalaDecompilerException =>
        Log.warn(s"Error decompiling class $className, ${e.getMessage}")
        None
      case cause: Exception =>
        Log.error(s"Error decompiling class $className", cause)
        None
    }

  private def containsMarker(text: Array[Byte]): Boolean = {
    val arrayLength = ScalaSigBytes.length
    text.length - arrayLength match {
      case delta if delta >= 1 =>
        var wordStartIdx = 0
        var innerIdx = 0

        while (wordStartIdx <= delta) {
          while (innerIdx < arrayLength && text(wordStartIdx + innerIdx) == ScalaSigBytes(innerIdx)) {
            innerIdx += 1
          }
          if (innerIdx == arrayLength) return true
          else {
            wordStartIdx += 1
            innerIdx = 0
          }
        }

        false
      case _ => false
    }
  }

}