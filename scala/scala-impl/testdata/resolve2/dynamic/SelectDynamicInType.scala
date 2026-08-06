import scala.language.dynamics
trait Foo {
  type T
}
object Foo extends Dynamic {
  def selectDynamic(name: String) : Foo = new Foo {}
}
type A = Foo./* resolved: false */`5`.T