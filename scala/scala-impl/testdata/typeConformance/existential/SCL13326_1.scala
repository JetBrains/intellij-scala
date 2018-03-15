trait Type { type S}
trait Base {
  type T <: Type
  var f: x.type#S forSome {val x: T}
}

abstract class Derived extends Base {
  override type T = Type
  /*caret*/
  val ff: x.type#S forSome{val x: T} = f
}
//True