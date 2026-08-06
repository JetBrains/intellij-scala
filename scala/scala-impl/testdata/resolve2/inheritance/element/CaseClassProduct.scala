sealed abstract class Base
case class Foo(a: Int) extends Base
val aa = Foo(0)
aa./* */productArity