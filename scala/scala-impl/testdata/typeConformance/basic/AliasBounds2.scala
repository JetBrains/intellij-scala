trait Names {
  type Name >: Null <: NameApi
  type TermName >: Null <: Name
  abstract class NameApi

}
object N extends Names
val x: N.Name = (null: N.TermName)
//True