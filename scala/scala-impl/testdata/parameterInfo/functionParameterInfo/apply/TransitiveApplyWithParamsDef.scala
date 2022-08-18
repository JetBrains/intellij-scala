trait TraitWithApply {
  def apply(i: Int, s: String): Unit = {}
}

object TransitiveApplyWithParamsDef {
  def foo(b: Boolean): TraitWithApply = new TraitWithApply {}
}

object Test {
  TransitiveApplyWithParamsDef.foo(<caret>)
}
//b: Boolean