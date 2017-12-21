import scala.language.dynamics
trait Foo {
  type T
}
object Foo extends Dynamic {
  def selectDynamic(name: String) : Foo = new Foo {}
}
type A = Foo.<ref>`5`.T