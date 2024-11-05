package member

trait Given {
  trait T1

  trait T2

  given alias: T1 = ???

  given T1 = ???

  given abstractInstance: T1

  given instance1: T1 with {}

  given instance2: {} with {}

  given T2 with {}

  given {} with {}
}