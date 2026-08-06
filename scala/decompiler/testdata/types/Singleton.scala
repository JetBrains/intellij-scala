package types

trait Singleton {
  type T = Int.type

  val TERMREF/**//*: None.type*/ = /**/None/*???*/

  val SHAREDTYPE/**//*: None.type*/ = /**/None/*???*/

  case object CaseObject

  def caseObject/**//*: CaseObject.type*/ = /**/CaseObject/*???*/
}