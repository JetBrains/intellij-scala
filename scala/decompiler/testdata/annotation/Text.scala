package annotation

trait Text {
  @deprecated("message")
  def f1: Int = ???

  @deprecated("message", "since")
  def f2: Int = ???

  @Deprecated(since = "since")
  def f3: Int = ???

  @deprecated("""line1
line2
""")
  def f4: Int = ???
}