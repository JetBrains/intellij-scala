object Test {
  abstract class TreeInfo {
    val trees: SymbolTable
    import trees._
    def goo(tree: Tree): Boolean = false
    def goo(x: Boolean): Boolean = true
  }
  trait rTrees {
    abstract class Tree extends Product {
      val x = 1
    }
  }
  trait Trees extends rTrees {
    self: SymbolTable =>
    object treeInfo extends {
    val trees : Trees.this.type = Trees.this
    } with TreeInfo
  }
  abstract class SymbolTable extends Trees
  abstract class Global extends SymbolTable
  trait Analyzer {
    val global: Global
  }

  trait Typers {
    self: Analyzer =>
    import global._
    private def argMode(fun : Tree) = {
      treeInfo./* line: 5 */goo(fun)
    }
  }
}