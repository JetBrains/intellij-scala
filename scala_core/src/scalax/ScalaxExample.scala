package scalax

import java.io.ByteArrayOutputStream
import rules.scalasig._
import scala.collection.mutable.{Set, HashSet}

/**
 * @author ilyas
 */

object ScalaxExample {

  import scalax.rules.scalasig.Symbol

  def checkOccurred(set: scala.collection.immutable.Set[Symbol], s: Symbol): Boolean = {
    s match {
      case c@(_: ClassSymbol | _: ObjectSymbol) if c.isInstanceOf[SymbolInfoSymbol] =>
        if (set.contains(c)) true
        else checkOccurred(set, c.asInstanceOf[SymbolInfoSymbol].symbolInfo.owner)
      case _ => false
    }
  }

  def main(args: Array[String]) {
    //    val st = "scala.Seq"
    //    val st = "scalax.rules.Parsers"
    //    val st = "scala.util.parsing.combinatorold.Parsers"
//        val st = "scala.collection.immutable.Set"
//        val st = "scala.dbc.syntax.DataTypeUtil"
//    val st = "scala.dbc.Vendor"
//    val st = "scala.Iterable"
//    val st = "scala.Predef"
    val st = "scalax.Set"
    val clazz = Class.forName(st)
    val byteCode = ByteCode.forClass(clazz)
    val classFile = ClassFileParser.parse(byteCode)
    val sig = classFile.attribute("ScalaSig").map(_.byteCode)
    val stream = new ByteArrayOutputStream
    sig.map(ScalaSigAttributeParsers.parse) match {
      case Some(scalaSig) => {
        Console.withOut(stream){
          val owner = ((scalaSig.topLevelClass, scalaSig.topLevelObject) match {
//            case (Seq(c, _*), _) => c.symbolInfo.owner
            case (_, Some(o)) => o.symbolInfo.owner
            case _ => ""
          }).toString
          if (owner.length > 0) {print("package "); print(owner)}
          {println; println}
          // Print classes
          for (c <- scalaSig.topLevelClass) ScalaSigPrinter.printSymbol(c)
          println
          for (o <- scalaSig.topLevelObject) ScalaSigPrinter.printSymbol(o)
        }
      }
    }
    println(stream.toString)
  }
}
