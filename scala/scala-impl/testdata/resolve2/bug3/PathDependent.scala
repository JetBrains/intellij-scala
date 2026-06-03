object K {
  class U {
    type Tr
    class T {
      def foo(tr: Tr) {}
      def foo(x: Int) {}
    }
  }


  class G extends U


  trait A {
    val g : G = new G
    object Z extends g.T {
      override def foo(tr : g.Tr) {
        super./* line: 5 */foo(tr)
      }
    }
  }
}