class P extends SportsmanItem("text")

class SportsmanItem(user: String) extends TreeItem[SportsmanItem]

trait TreeItem[+T <: TreeItem[T]]

abstract class TT {
  def foo(x: T forSome {type T <: TreeItem[T]}) = 1
  def foo(s: String) = "text"
  /*start*/foo(new SportsmanItem("text"))/*end*/
}
//Int