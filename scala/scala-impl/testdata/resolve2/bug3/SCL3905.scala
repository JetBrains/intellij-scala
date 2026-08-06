object SCL3905 {

  def foo(a: String): Option[String] = null

  Seq("")./*resolved: true*/flatMap(a => foo(a))
  Seq("")./*resolved: true*/flatMap(foo(_))
  Seq("")./*resolved: true*/flatMap(foo _)
  Seq("")./*resolved: true*/flatMap(foo)

  class A
  def blip(a: String): A = null
  val conv = (a: A) => Seq("")

  Seq("")./*resolved: true*/flatMap {
    a =>
      implicit val conv1 = conv
      blip(a)
  }
  Seq("")./*resolved: true*/flatMap {
    implicit val conv1 = conv
    foo(_)
  }
  Seq("")./*resolved: true*/flatMap {
    implicit val conv1 = conv
    foo _
  }
  Seq("")./*resolved: true*/flatMap {
    implicit val conv1 = conv
    foo
  }

  def bar[A](a: String): Option[A] = null

  Seq("")./*resolved: true*/flatMap(a => bar(a))
  Seq("")./*resolved: true*/flatMap(bar(_))
  Seq("")./*resolved: true*/flatMap(bar _)
}
