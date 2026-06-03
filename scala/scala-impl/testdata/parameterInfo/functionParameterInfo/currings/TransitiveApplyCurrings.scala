trait TraitWithApply {
  def apply(i: Int)(s: String): Unit = {}
}

object TransitiveApplyCurrings {
  def foo: TraitWithApply = new TraitWithApply {}
}

object Test {
  TransitiveApplyCurrings.foo(<caret>)
}
//TEXT: (i: Int)(s: String), STRIKEOUT: false