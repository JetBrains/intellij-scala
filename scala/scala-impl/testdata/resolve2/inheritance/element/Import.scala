object Holder {
  def f {}
  class C
  case class CC
  case object CO
  trait T
  object O
  val v1: Int = 1
  var v2: Int = 2
  type A = Int
}

class C1 {
  import Holder.f
  import Holder.C
  import Holder.CC
  import Holder.CO
  import Holder.T
  import Holder.O
  import Holder.A
  import Holder.v1
  import Holder.v2
}

class C2 extends C1 {
  println(/* resolved: false */ f)
  println(/* resolved: false */ C)
  println(/* resolved: false */ CC)
  println(/* resolved: false */ CO)
  println(/* resolved: false */ T)
  println(/* resolved: false */ O)
  println(/* resolved: false */ A)
  println(/* resolved: false */ v1)
  println(/* resolved: false */ v2)
}