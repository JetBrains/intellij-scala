trait HasTarget  { type Target }
trait HasBuilder { type Builder }
trait Builder1 extends HasTarget  { def target: Target = ??? }
trait Action1  extends HasBuilder { type Builder = Builder1 }
trait TCons[H <: HasBuilder, T <: HasBuilder] { type Builder = H#Builder with T#Builder }

trait Foo                     { def bar     = true }
trait MkFoo extends HasTarget { type Target = Foo  }

class Repro {
  type Actions = TCons[Action1, HasBuilder]

  def ko1: Actions#Builder with MkFoo = ???
  ko1.target./*resolved:true*/bar // good code red, "cannot resolve symbol bar"
}
