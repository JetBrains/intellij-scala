trait Trees {
  type Symbol = Int
  class Tree {
    def symbol: Symbol = 2
  }
}

class Global extends Trees {

}

trait Typers {
  self: Analyzer =>
  import global._

  def foo(tree: Tree) {
    /*start*/tree.symbol/*end*/
  }
}

class Analyzer {
  val global: Global = new Global
}
()
//Typers.this.global.Symbol