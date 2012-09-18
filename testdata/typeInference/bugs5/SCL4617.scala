package foo

package object Test {
  object Bar {
    def apply(s: String): String = s
    def apply(l: Long): Long = l
  }
}

object Foo {
  // marked as red but Bar.apply(4) is fine
  // removing the 1st apply in Bar removes the warning as well
  val x = /*start*/Bar(4)/*end*/
}
//Long