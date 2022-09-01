package types

trait This {
  type T1 = this.type

  val v/**//*: types.This1.This2.type*/ = /**/This1.This2/*???*/
}/**/
object This1 {
  object This2
}/**/