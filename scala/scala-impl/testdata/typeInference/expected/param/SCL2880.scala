object Main {
  class A()
  case class RichA() {
    def apply(u: Int => Int): Boolean = false
  }
  implicit def toRichA(a: A): RichA = null
  val a = new A()
  a(z => /*start*/z/*end*/)
}
//Int