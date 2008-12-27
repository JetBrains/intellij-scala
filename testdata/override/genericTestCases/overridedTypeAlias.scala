implement y
package Test

abstract class Aa {
  type K = Aa
  val y: K
}

class OverridedTypeAlias extends Aa {
  override type K = Bfg

  <caret>
}<end>
package Test

abstract class Aa {
  type K = Aa
  val y: K
}

class OverridedTypeAlias extends Aa {
  override type K = Bfg


  val y: Aa = _
}