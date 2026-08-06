class SelfQualifier { self =>
  private[this] val aPrivateVal: Int = 0
  protected[this] val aProtectedVal: Int = 0
  private[this] var aPrivateVar: Int = 0
  protected[this] var aProtectedVar: Int = 0
  private[this] def aPrivateFun: Int = 0
  protected[this] def aProtectedFun: Int = 0

  // access to following members is highlighted as a syntax error
  self./* */aPrivateVal
  self./* */aPrivateVal
  self./* */aProtectedVal
  self./* */aPrivateVar
  self./* */aProtectedVar
  self./* */aPrivateFun
  self./* */aProtectedFun
}