implement y
package Test

abstract class Aa {
  type K = Aa
  val y: K
}

class OverridedTypeAlias extends Aa {
  override type K = B

  <caret>
}<end>
package Test

abstract class Aa {
    type K = Aa
    val y: K
}

class OverridedTypeAlias extends Aa {
    override type K = B


    val y: Aa = _
}