package scala.tasty
package reflect

trait TreeMap {
  val reflect: Reflection
  import reflect._

  def transformTree(tree: Tree)(implicit ctx: Context): Tree = ???
  def transformStatement(tree: Statement)(implicit ctx: Context): Statement = ???
  def transformTerm(tree: Term)(implicit ctx: Context): Term = ???
  def transformTypeTree(tree: TypeTree)(implicit ctx: Context): TypeTree = ???
  def transformCaseDef(tree: CaseDef)(implicit ctx: Context): CaseDef = ???
  def transformTypeCaseDef(tree: TypeCaseDef)(implicit ctx: Context): TypeCaseDef = ???
  def transformStats(trees: List[Statement])(implicit ctx: Context): List[Statement] = ???
  def transformTrees(trees: List[Tree])(implicit ctx: Context): List[Tree] = ???
  def transformTerms(trees: List[Term])(implicit ctx: Context): List[Term] = ???
  def transformTypeTrees(trees: List[TypeTree])(implicit ctx: Context): List[TypeTree] = ???
  def transformCaseDefs(trees: List[CaseDef])(implicit ctx: Context): List[CaseDef] = ???
  def transformTypeCaseDefs(trees: List[TypeCaseDef])(implicit ctx: Context): List[TypeCaseDef] = ???
  def transformSubTrees[Tr <: Tree](trees: List[Tr])(implicit ctx: Context): List[Tr] = ???
}
