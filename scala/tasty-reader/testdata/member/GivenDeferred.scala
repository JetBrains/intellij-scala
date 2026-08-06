package member

trait GivenDeferred {
  trait T1

  trait T2

  given deferred: T1 = scala.compiletime.deferred

  given T1 = scala.compiletime.deferred

  given alias: T2 = ???

  given T2 = ???
}