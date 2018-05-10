implicit class WithTag[T](x: T) {
  def tagged[U]: T @@ U = x.asInstanceOf[T @@ U]
}
implicit class WithUntag[T, U](x: T @@ U) {
  def untagged: T = x.asInstanceOf[T]
}

type Tagged[U]
type @@[T, U] = T with Tagged[U]

trait Tag
def printTagged(int: Int @@ Tag): Unit = {
  println(s"got tagged ${int.<ref>untagged}")
}

printTagged(5.tagged)

println(s"got untagged: ${9.tagged.untagged}")