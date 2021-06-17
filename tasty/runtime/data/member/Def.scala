package member

trait Def {
  def declaration: Int

  def definition: Int = ???

  inline def inlineDefinition: Int = ???

  inline def inlineParameter(inline x: Int): Int = ???
}