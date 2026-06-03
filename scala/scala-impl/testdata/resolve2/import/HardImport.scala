class O {
  class A {
    case class A
  }
  import C.A
  object C extends /* line: 2 */A
  /* */A
}

class A {
  case class A
}
import C.A
object C extends /* line: 10 */A
/* */A