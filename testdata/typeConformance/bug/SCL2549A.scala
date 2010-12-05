trait Base {
  type BA <: {
    type X <: Any
  }

  def x: BA#X
}

trait Sub extends Base {
  override type BA = {
    type X = Int
  }
}
val s: Sub = null

val x: Int = s.x
// True