
object Repro {
  trait HasBuilder {
    type Builder
  }

  trait TList {
    type Builder
  }
  trait TNil extends TList {
    type Builder
  }

  trait TCons[H <: HasBuilder, T <: TList] extends TList {
    type Builder = H#Builder with T#Builder
  }

  class Action1 extends HasBuilder {
    type Builder = Action1Builder
  }
  trait Action1Builder {
    type TargetType
    def target(__DEBUG__ : Any = ""): TargetType = ???
  }

  trait HasTargetType[T] {
    type TargetType = T
  }
  type ActionParam = TCons[Action1, TNil]
//  def action1NOK1: ActionParam#Builder { type TargetType = TargetType1 } = ???
  def action1NOK2: HasTargetType[TargetType1] with ActionParam#Builder = ???

//  def action1OK1: TCons[Action1, TNil]#Builder { type TargetType = TargetType1 } = ???
//  def action1OK2: Action1#Builder { type TargetType = TargetType1 } = ???
//  def action1OK3: HasTargetType[TargetType1] with TCons[Action1, TNil]#Builder = ???
//  def action1OK4: HasTargetType[TargetType1] with Action1#Builder = ???

  class TargetType1 {
    def ok = true
  }

//  action1OK1.target.ok
//  action1OK2.target.ok
//  action1OK3.target.ok
//  action1OK4.target.ok
  // good code red, "cannot resolve symbol ok"
//  action1NOK1.target.ok
  action1NOK2.target()./*resolved:true*/ok
}