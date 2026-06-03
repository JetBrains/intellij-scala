object PathDep {
  trait Context {
    class Tree
  }

  val c = new Context {}
  val t = new c.Tree
  val util = new Util[c.type](c)
  /*start*/util.id(t)/*end*/ // expected PathDep.Util#context#Tree, actual: PathDep.c.type#Tree

  class Util[C <: Context](val context: C) {
    def id(s: String) = s
    def id(a: context.Tree) = 123
  }
}
//Int