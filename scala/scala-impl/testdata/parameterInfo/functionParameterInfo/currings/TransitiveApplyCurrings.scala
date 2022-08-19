trait TraitWithApply {
  def apply(i: Int)(s: String): Unit = {}
}

object TransitiveApplyCurrings {
  def foo: TraitWithApply = new TraitWithApply {}
}

object Test {
  TransitiveApplyCurrings.foo(<caret>)
}
//(i: Int)(s: String)