case class A()

object Show {
  implicit class ShowExtension[T](x: T)(implicit show: Show[T]) {
    def ext = x
  }
}
trait Show[T]

object ShowImplicits {
  implicit object InstanceA extends Show[A]
}

object Example {
  import Show.ShowExtension
  import ShowImplicits.InstanceA // marked as not used

  A().ext
}
/*
case class A()

object Show {
  implicit class ShowExtension[T](x: T)(implicit show: Show[T]) {
    def ext = x
  }
}
trait Show[T]

object ShowImplicits {
  implicit object InstanceA extends Show[A]
}

object Example {
  import Show.ShowExtension
  import ShowImplicits.InstanceA // marked as not used

  A().ext
}
 */