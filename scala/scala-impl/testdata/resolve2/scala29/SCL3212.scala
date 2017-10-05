package scala {

trait T {
  @scala.annotation.bridge
  def bridge(a: String) = 0

  @scala.annotation.bridge
  def foo(a: String) = 0

  def foo(a: Any) = 0

  /*resolved: false*/ bridge
  /*resolved: true, line: 10, applicable: false*/ foo
}


}
