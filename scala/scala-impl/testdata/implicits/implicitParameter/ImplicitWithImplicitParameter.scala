object Test {
 trait A
 trait B

 trait Builder[From, To] {
  def buildFrom(x: From): To
 }

 implicit val a2bBuilder = new Builder[A, B] {
  override def buildFrom(x: A) = new B{}
 }

 implicit def a2b[From, To >: B](x: From)(implicit bl: Builder[From, To]): To = bl.buildFrom(x)

 def f(b: B) = println(b)

 def main(args: Array[String]) {
  val a:  A = new A
  f(/*start*/a/*end*/)
 }
}
/*
Seq(a2b,
    any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt),
Some(a2b)
*/