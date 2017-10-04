object NullResultHighlight extends App
{
  def f[T >: Null](t : T) : T = {
    object A {
      def foo(t: T) = 1
      def foo(x: Int) = 2
    }
    A./* line: 5 */foo(null)
    if (t == null) null else t
  }

  def goo(x: Nothing) = 1
  def goo(x: Any) = 2

  /* line: 13 */goo(null)
}