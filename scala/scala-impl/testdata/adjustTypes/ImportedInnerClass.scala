object ABC {
  class BCE
}

object Test {
  import ABC.BCE

  val x: /*start*/ABC.BCE/*end*/ = new BCE()
}
/*
object ABC {
  class BCE
}

object Test {
  import ABC.BCE

  val x: BCE = new BCE()
}
*/