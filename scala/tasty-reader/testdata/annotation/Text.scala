package annotation

trait Text {
  @deprecated("message")
  def f1: Int = ???

  @deprecated("message", "since")
  def f2: Int = ???

  @Deprecated(since = "since")
  def f3: Int = ???

  @deprecated("line1\nline2")
  def f4: Int = ???

  @throws[RuntimeException]
  def f5: Int = ???

  @throws[RuntimeException]("cause")
  def f6: Int = ???

  @SuppressWarnings(Array("one", "two"))
  def f7: Int = ???
}