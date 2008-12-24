package org.jetbrains.plugins.scala.decompiler

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
//        val st = "scala.util.parsing.combinatorold.Parsers"
    //        val st = "scala.collection.immutable.Set"
    //       val st = "scala.dbc.syntax.DataTypeUtil"
//        val st = "scala.tools.nsc.typechecker.Typers"
//        val st = "org.jetbrains.plugins.scala.decompiler.Tupo"
//        val st = "scala.xml.Elem"
    val st = "scala.Predef"
    val clazz = Class.forName(st)
    val byteCode = ByteCode.forClass(clazz)
    val classFile = ClassFileParser.parse(byteCode)
    val Some(sig) = classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse)

    val Some(sf) = classFile.attribute("SourceFile").map(_.byteCode)

//    println(classFile.header.constants(signature.sourceFileIndex))

//    val Some(bc) = classFile.attribute("SourceFile").map(_.byteCode).map(_.take(6))
//    println(bc)

    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
    val printer = new ScalaSigPrinter(stream)
    for (c <- sig.topLevelClasses) printer.printSymbol(c)
    for (c <- sig.topLevelObjects) printer.printSymbol(c)
    println(baos.toString)
  }
}
