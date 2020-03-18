package scala.tasty
package reflect

trait TreeTraverser extends TreeAccumulator[Unit] {
  import reflect._

  def traverseTree(tree: Tree)(implicit ctx: Context): Unit = ???
  def foldTree(x: Unit, tree: Tree)(implicit ctx: Context): Unit = ???
  protected def traverseTreeChildren(tree: Tree)(implicit ctx: Context): Unit = ???
}
