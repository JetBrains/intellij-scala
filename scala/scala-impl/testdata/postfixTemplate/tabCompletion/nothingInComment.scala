package tests

object Example {
  def test(): Unit = {
    // SCL-17630
    Seq(1, 2, 3)
      // TODO: is this ok? e.g<caret>
      .filter(_ == 42)
      .map(_ * 2)
  }
}
