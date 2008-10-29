implement y
abstract class Aa {
  type K = Aa
  val y: K
}

class OverridedTypeAlias extends Aa {
  override type K = B

  <caret>
}<end>
abstract class Aa {
    type K = Aa
    val y: K
}

class OverridedTypeAlias extends Aa {
    override type K = B


    val y: Aa = _
}