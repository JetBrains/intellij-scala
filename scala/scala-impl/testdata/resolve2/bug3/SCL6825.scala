object SCL6825 {
  object K {
    def size(): Int = 123
  }

  object L {
    val size : Int = 124
  }

  object M {
    def size: Int = 125
  }

  def foo(x: AnyRef {val size: Int}) = 1
  def foo(b: Boolean) = 2
  def goo(x: AnyRef {def size(): Int}) = 2
  def goo(b: Boolean) = 3
  def zoo(x : AnyRef {def size: Int}) = 3
  def zoo(b: Boolean) = 4

  /* resolved: false */foo(K)
  /*                 */foo(L)
  /* resolved: false */foo(M)
  /*                 */goo(K)
  /* resolved: false */goo(L)
  /* resolved: false */goo(M)
  /* resolved: false */zoo(K)
  /*                 */zoo(L)
  /*                 */zoo(M)
}