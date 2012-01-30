trait Vendor[T] extends Function0[T] {
  implicit def vend: T
  def apply() = vend
}

object Vendor {
  implicit def fun2vend[T](f: () => T): Vendor[T] = null
}

trait Factory {
  abstract class FactoryMaker[T](s: Vendor[T])(implicit m: Manifest[T]) extends Vendor[T] {
    implicit def vend = s.apply()
  }
}

object ServiceFactory extends Factory {
  implicit object systemClock extends FactoryMaker(() => 1)
  implicit object login extends FactoryMaker(() => 1)
}

object Login {
  import ServiceFactory._

  /*start*/login()/*end*/
}
//Int