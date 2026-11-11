package member

trait This {
  class Class[A] {
    def this(x: Int) = /**/this()/*???*/

    def this(x: Int, y: Long) = /**/this(1)/*???*/
  }
}