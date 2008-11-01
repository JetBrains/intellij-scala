package scalax

import rules.scalasig._
import scala.collection.mutable.{Set, HashSet}

/**
 * @author ilyas
 */

object ScalaxExample {

  import scalax.rules.scalasig.Symbol

  def checkOccurred(set: Set[Symbol], s: Symbol): Boolean = {
    s match {
      case c@(_: ClassSymbol | _: ObjectSymbol) if c.isInstanceOf[SymbolInfoSymbol]=>
        if (set.contains(c)) true
        else checkOccurred(set, c.asInstanceOf[SymbolInfoSymbol].symbolInfo.owner)
      case _ => false
    }
  }

  def main(args: Array[String]) {
    val st = "scala.Seq"
    //    val st = "scalax.rules.Parsers"
    val clazz = Class.forName(st)
    val res = ScalaSigParser.parse(clazz)
    res match {
      case Some(sig) => {
        val symbols = sig.symbols

        val set = new HashSet[Symbol]
        for (s <- symbols) {
          if (!set.contains(s)) {
            s match {
              case c@(_: ClassSymbol | _: ObjectSymbol) if !checkOccurred(set, c) => ScalaSigPrinter.printSymbol(c); set += s
              case _ => {}
            }
          }
        }
      }
      case None =>
    }
  }
}
