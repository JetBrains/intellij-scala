trait TraitWithApply {
  def apply(i: Int, s: String): Unit = {}
}

object TransitiveApplyNoParamsDef {
  def foo: TraitWithApply = new TraitWithApply {}
}

object Test {
  TransitiveApplyNoParamsDef.foo(<caret>)
}
//TEXT: i: Int, s: String, STRIKEOUT: false