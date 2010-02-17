case class CC {
  def f = {}
}

val q: CC = new CC

println(q./* line: 2 */f)

