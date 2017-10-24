trait A {
  protected val x = 0
}

trait B {
  self: Any with A =>

  /*resolved: true*/x
}

