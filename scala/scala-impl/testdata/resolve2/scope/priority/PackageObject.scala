package object holder {
  class C
  object O
}

package holder {
class C
object O
trait T {
  println(/* line: 8 */ O.getClass)
  println(classOf[ /* line: 7 */ C])
}
}