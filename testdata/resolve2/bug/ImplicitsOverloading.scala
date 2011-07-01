1
class a
object a {
  implicit def a2zest(a: a): zest = new zest
}
class test {
  def foo = 1
  def goo = 1
}
class zest {
  def goo = 2
  def zoo = 2
}

object pest {
  implicit def a2test(a: a): test = new test
}

object G {
  import pest._
  val t: a = new a
  println(t./* line: 8 */goo)
  println(t./* line: 12 */zoo)
  println(t./* line: 7 */foo)
}