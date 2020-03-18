package scala.tasty
package reflect

trait TreeAccumulator[X] {
  val reflect: Reflection
  import reflect._

  def foldTree(x: X, tree: Tree)(implicit ctx: Context): X
  def foldTrees(x: X, trees: Iterable[Tree])(implicit ctx: Context): X = ???
  def foldOverTree(x: X, tree: Tree)(implicit ctx: Context): X = ???
}
