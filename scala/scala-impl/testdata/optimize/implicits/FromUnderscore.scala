// Notification message: null
trait T
trait U {
  def doSomething(something: Any)(implicit t: T): U = ???
}

object Sample {
  implicit val tInstance: T = ???
}

object Usecase {
  import Sample.tInstance

  val x: U => U = _.doSomething("test")
}
/*
trait T
trait U {
  def doSomething(something: Any)(implicit t: T): U = ???
}

object Sample {
  implicit val tInstance: T = ???
}

object Usecase {
  import Sample.tInstance

  val x: U => U = _.doSomething("test")
}
*/