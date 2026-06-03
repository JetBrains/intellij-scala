object test {
  // this
  class adfa(a: Int) {
    def this() {

    }
  }
  new adfa()


  val x = new /*caret*/adfa(2)
  val y = new adfa()
}
/*
object test {
  // this
  class NameAfterRename(a: Int) {
    def this() {

    }
  }
  new NameAfterRename()


  val x = new /*caret*/NameAfterRename(2)
  val y = new NameAfterRename()
}
*/