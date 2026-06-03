enum TestEnum [MyTypeParameter](myParameter: Int) {
  /**
   * @param myParameterInner42 parameter description          ## line: 6, type: org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl ##
   * @tparam MyTypeParameterInner type parameter description  ## line: 6, type: org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl ##
   */
  case EnumMember[MyTypeParameterInner](myParameterInner42: Int)
    extends TestEnum[MyTypeParameterInner](myParameterInner42)
}