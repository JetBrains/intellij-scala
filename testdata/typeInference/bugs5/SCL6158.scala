object O {

  trait TypeTag
  type @@[O <: AnyRef, T <: TypeTag] = O with T

  sealed trait X extends TypeTag {
    type YTag <: TypeTag
    type Y = String @@ YTag
  }

  class F[Tag <: X] {
    type Y = Tag#Y
    def apply(): Y = null.asInstanceOf[Y]
  }

  trait ExampleTag extends X {
    override type YTag = ExampleTagY
  }
  sealed trait ExampleTagY extends TypeTag
  type Example = AnyRef @@ ExampleTag
  type ExampleY = String @@ ExampleTagY

  val ExampleF = new F[ExampleTag]

  def foo1(y: ExampleY) = 1
  def foo1(s: String) = s

  /*start*/foo1(ExampleF())/*end*/
}
//Int