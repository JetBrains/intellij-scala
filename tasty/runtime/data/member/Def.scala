package member

trait Def {
  def declaration: Int

  def definition: Int = ???

  def definitionTypeRef/**//*: Int*/ = /**/1/*???*/

  inline def inlineDefinition: Int = ???

  inline def inlineParameter(inline x: Int): Int = ???
}