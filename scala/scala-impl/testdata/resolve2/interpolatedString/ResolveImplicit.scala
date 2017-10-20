val blah = 1
object A {
  class E {
    def a(i: Int*) = i.length
    def b(a: Any*) = a.length
    def c(a: String, b: String) = a + b
    def c(a: Int, b: String) = a + b.length
  }
  
  implicit def extendContext(ctx: StringContext) = new E
  
  val x = "a"
  val y = "y"

  
  val t1 = /*resolved: true, applicable: true*/ a"blah blah ${1} ${2} blah "
  val t2 = /*resolved: true, applicable: true*/ b"blah ${1} blah ${x} blah "
  val t3 = /*resolved: true, applicable: true*/ c"blah ${x} blah ${y}"
  val t4 = /*resolved: true, applicable: true*/ c"blah ${1} blah ${y}"
}

