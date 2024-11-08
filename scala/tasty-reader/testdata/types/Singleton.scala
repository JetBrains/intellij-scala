package types

trait Singleton {
  type T = Int.type

  val TERMREF/**//*: None.type*/ = /**/None/*???*/

  val SHAREDTYPE/**//*: None.type*/ = /**/None/*???*/

  val APPLIED_TERMREF/**//*: scala.collection.immutable.Seq[Option.type]*/ = /**/Seq(Option)/*???*/

  case object CaseObject

  def caseObject/**//*: CaseObject.type*/ = /**/CaseObject/*???*/
}