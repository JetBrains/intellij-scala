object WrongImplicitResolution {

  trait CanDo[-A, +B] {
    def apply(x: A): B
  }

  case class Value[A](x: A) {
    def doIt[That](implicit ev: CanDo[A, That]): That =
      ev(x)
  }

  implicit def canDoNothing[A]: CanDo[A, Value[A]] = new CanDo[A, Value[A]] {
    override def apply(x: A): Value[A] = Value(x)
  }

  trait CanDoBetter[-A, +B] extends CanDo[A, B]

  trait SomeEvidence[A] {
    def apply(x: A): A
  }

  implicit def canDoBetter[A](implicit ev: SomeEvidence[A]): CanDoBetter[A, Value[A]] = new CanDoBetter[A, Value[A]] {
    override def apply(x: A): Value[A] = Value(ev(x))
  }

  implicit val intSomeEvidence: SomeEvidence[Int] = new SomeEvidence[Int] {
    override def apply(x: Int): Int = x + 1
  }

  def foo(): Unit = {
    val special = Value(5)
    val problem = special.doIt.<ref>doIt
  }

}