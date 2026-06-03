object Test extends App {
  (1: Any) match {
    case one: 1 => true
    case _ => false
  }                                  // result is true: Boolean
  (1: Any).isInstanceOf[1]           // result is true: Boolean
}