object test {
  // this
  class adfa(a: Int) {
    def this() {

    }
  }
  new adfa()


  val x = new adfa(2)
  val y = new /*caret*/adfa()
}
/*
object test {
  // this
  class NameAfterRename(a: Int) {
    def this() {

    }
  }
  new NameAfterRename()


  val x = new NameAfterRename(2)
  val y = new /*caret*/NameAfterRename()
}
*/