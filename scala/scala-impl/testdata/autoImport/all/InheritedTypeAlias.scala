trait AAA {
  type BBB = String
}

object AAA extends AAA

object CCC {
  val bbb: /*ref*/BBB = ???
}
/*
import AAA.BBB

trait AAA {
  type BBB = String
}

object AAA extends AAA

object CCC {
  val bbb: /*ref*/BBB = ???
}
*/