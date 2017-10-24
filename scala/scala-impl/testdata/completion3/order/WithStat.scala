object WithStat {
  val a = new A
  a.<caret>

  class A {
    def fboo = ???
    def fbar = ???
  }
}
