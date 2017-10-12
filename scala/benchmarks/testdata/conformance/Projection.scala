trait A {
  type X
}

trait Base {
  type BA <: A

  def x: BA#X
}

trait Sub extends Base {
  trait BA extends A {
    type X = Int
  }
}
val s: Sub = null

val x: Int = s.x
// True