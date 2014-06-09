trait A {
  def foo = 123
}

val z = new { val i = 1 } with A

/*start*/z.i/*end*/
//Int