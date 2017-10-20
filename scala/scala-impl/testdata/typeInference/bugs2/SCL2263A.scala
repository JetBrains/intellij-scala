class Outer {
	class Inner
}

object Obj {
 val outer = new Outer
 val inner = new outer.Inner
}

object O {
 val o = new Outer
 val i = /*start*/new o.Inner/*end*/
 def m {
  val o = new Outer
  val i = new o.Inner
 }
}
//O.o.Inner