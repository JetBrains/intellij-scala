trait test {
  trait Tree extends Map[Tree, Int]{
    def m = 1
  }
  def top[Item <: Map[Item, Int]](t:List[Item]) = t.head
  implicit def f(t:String):List[Tree] = sys.error("todo")

  top(null.asInstanceOf[List[Tree]]).m
  /*start*/top("hello").m/*end*/ //red here, implicit is not considered
}
//Int