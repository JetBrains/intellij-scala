abstract class TreeInfo {
  val global: Global
  import global._
  def foo(tree: Tree): Boolean = false
  def foo(x: Boolean): Boolean = true
}

class Global {
  class Tree
  object treeInfo extends TreeInfo {
    val global: Global.this.type = Global.this
  }
}

trait Test {
  val g: Global
  import g._
  def arg(fun: Tree) = treeInfo./* line: 4 */foo(fun)
}
