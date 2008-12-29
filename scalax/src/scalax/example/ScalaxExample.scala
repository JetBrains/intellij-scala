package scalax.example

/**
 * @author ilyas
 */

import java.io.{PrintStream, ByteArrayOutputStream}
import java.sql.Connection
import scalax.rules.scalasig._
import _root_.scala.collection.mutable.{Set, HashSet}
import scalax.rules.scalasig.{ObjectSymbol, SymbolInfoSymbol, ClassSymbol, ScalaSigPrinter}


/**
 * @author ilyas
 */

object ScalaxExample {
  import scalax.rules.scalasig.Symbol

  def main(args: Array[String]) {
    //    val st = "scala.Seq"
    //    val st = "scalax.rules.Parsers"
    //    val st = "scala.util.parsing.combinatorold.Parsers"
    //    val st = "scala.collection.immutable.Set"
    //    val st = "scala.dbc.syntax.DataTypeUtil"
    //    val st = "scala.tools.nsc.typechecker.Typers"
    //    val st = "org.jetbrains.plugins.scala.decompiler.Tupo"
//        val st = "scala.xml.Elem"
//    val st = "scala.Predef"
    val st = "scala.tools.nsc.Global"

    val clazz = Class.forName(st)
    val byteCode = ByteCode.forClass(clazz)
    val classFile = ClassFileParser.parse(byteCode)
    val Some(sig) = classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse)

    val Some(sf) = classFile.attribute("SourceFile").map(_.byteCode)

    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)

    val syms = sig.topLevelClasses ::: sig.topLevelObjects
    syms.first.parent match {
      case Some(p) if (p.name != "<empty>") => {
        stream.print("package ");
        stream.print(p.path);
        stream.print("\n\n")
      }
      case _ =>
    }
    val printer = new ScalaSigPrinter(stream)
    for (c <- syms) printer.printSymbol(c)
    println(baos.toString)
  }
}
