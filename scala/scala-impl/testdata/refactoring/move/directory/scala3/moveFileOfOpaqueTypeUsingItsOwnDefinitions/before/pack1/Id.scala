package pack1

lazy val instance: Int = 1

opaque type Id = Long

object Id {
  def make(n: Long): Id = {
    assert(instance > 0)
    n
  }
}
