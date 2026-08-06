trait Trees {
  class Tree
}

trait Global extends Trees {
  self: Trees =>

  object gen extends {
    val global: Global.this.type = Global.this
  } with AnyRef {
    def foo(t: Tree) = t
  }
}

trait Client {
  val global: Global

  import global.{gen, Tree}

  val t: Tree
  global.gen./* resolved: true, applicable: true*/foo(t) // okay
  gen./* resolved: true, applicable: true*/foo(t) // okay

  import global.gen._

  /* resolved: true, applicable: true*/ foo(t) // good code red
}
()