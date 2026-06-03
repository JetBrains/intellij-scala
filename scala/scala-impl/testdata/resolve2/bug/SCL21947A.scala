package example

object Hello extends App {
  val g = new nsc.Global
  println(new C[g.type](g).exprF(new g./* resolved: true */Block(Nil, g.EmptyTree)))
  println(g./* resolved: true */O./* resolved: true */treeMethod(g.EmptyTree))
}

class C[G <: nsc.Global](val global: G) {
  import global._
  def exprF(t: Tree): Int = t match {
    case Block(_, e) => e.f
    case _ => t.f
  }
}

object api {
  trait Trees {
    type Tree

    type Block <: Tree

    val Block: BlockExtractor

    trait T {
      def treeMethod(t: Tree): Int
    }

    abstract class BlockExtractor {
      def apply(stats: List[Tree], expr: Tree): Block
      def unapply(block: Block): Option[(List[Tree], Tree)]
    }
  }
}

object internal {
  abstract class SymbolTable extends Trees

  trait Trees extends api.Trees { self: SymbolTable => // removing the self type fixes it

    abstract class Tree { def f = 0 }

    object O extends T {
      def treeMethod(t: Tree) = t.f
    }

    case class Block(stats: List[Tree], expr: Tree) extends Tree
    object Block extends BlockExtractor

    case object EmptyTree extends Tree
  }
}

object nsc {
  class Global extends internal.SymbolTable
}
