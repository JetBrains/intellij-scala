package scalax

import java.io.{PrintStream, ByteArrayOutputStream}
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
//       val st = "scala.dbc.syntax.DataTypeUtil"
//    val st = "scala.dbc.Vendor"
//    val st = "scala.Iterable"
    //val st = "scala.Predef"
    val st = "scala.collection.Set"
    val clazz = Class.forName(st)
    val byteCode = ByteCode.forClass(clazz)
    val classFile = ClassFileParser.parse(byteCode)
    val sig = classFile.attribute("ScalaSig").map(_.byteCode)
    val Some(scalaSig) = ScalaSigParser.parse(Class.forName("scala.Predef"))
    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
    val printer = new ScalaSigPrinter(stream)
    for (c <- scalaSig.topLevelClasses) printer.printSymbol(c)
    for (c <- scalaSig.topLevelObjects) printer.printSymbol(c)
    println(baos.toString)
  }
}
