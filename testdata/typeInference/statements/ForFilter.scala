class ForFilter {
  class O {
    def foreach(x: Int => Unit): O = new O
    def filter(x: Int => Boolean): O = new O
  }

  object X {
    for (i <- new O if /*start*/i/*end*/ != 2) {}
  }
}
//Int