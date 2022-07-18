// Notification message: null
package someTrait

class Test[T <: SomeTrait] {
  def doSomething(o: T) {
    import o._
    some
  }
}
trait SomeTrait {
  def some : String
}
/*package someTrait

class Test[T <: SomeTrait] {
  def doSomething(o: T) {
    import o._
    some
  }
}
trait SomeTrait {
  def some : String
}*/