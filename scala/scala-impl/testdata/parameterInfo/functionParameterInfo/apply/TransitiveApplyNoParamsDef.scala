trait TraitWithApply {
  def apply(i: Int, s: String): Unit = {}
}

object TransitiveApplyNoParamsDef {
  def foo: TraitWithApply = new TraitWithApply {}
}

object Test {
  TransitiveApplyNoParamsDef.foo(<caret>)
}
//i: Int, s: String