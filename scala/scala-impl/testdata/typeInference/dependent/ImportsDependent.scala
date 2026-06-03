trait Abstract {
  class O
}

object First extends Abstract

object Second extends Abstract

object Test {
  import First.{O => FO}, Second.{O => SO}

  val x: FO = 1
  val zz: SO = 1
  /*start*/zz/*end*/
}
//Second.O