object api {
  trait Trees {
    type Tree
    type Block <: Tree
    val Block: BlockExtractor
    abstract class BlockExtractor {
      def unapply(block: Block): Option[Tree]
    }
  }
}

object internal {
  abstract class SymbolTable extends Trees
  trait Trees extends api.Trees { self: SymbolTable => // removing the self type fixes it
    class Tree { def f = 0 }
    case class Block(expr: Tree) extends Tree
    object Block extends BlockExtractor // Object creation impossible, since member unapply.. is not defined
  }
}

class C(val g: internal.SymbolTable) {
  import g._
  def expr(t: Tree): Int = {
    t match {
      case /* name: unapply */Block(e) => e./* resolved: true */f
      case _ => -1
    }
  }
}
