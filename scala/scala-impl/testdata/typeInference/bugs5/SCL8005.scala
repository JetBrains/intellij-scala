object SCL8005 {

  case class Person(name: String, age: Int)

  def usePf[T](pf: PartialFunction[T,Unit]) : Function[T,Unit] = null

  def checkAge: Function[Person,Unit] = usePf {
    case p =>
      /*start*/p.age/*end*/
      1
  }
}
//Int