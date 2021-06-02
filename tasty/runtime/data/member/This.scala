package member

trait This {
  class Class {
    def this(x: Int) = /*???*/this()/**/

    def this(x: Int, y: Int) = /*???*/this(???)/**/
  }
}