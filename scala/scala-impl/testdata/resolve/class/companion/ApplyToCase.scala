class ApplyToCase {
  object Y {
    def foo: Int = 34
    def apply(x: Boolean): Y = Y(23)
  }

  case class Y(x: Int)

  <ref>Y(23)
}