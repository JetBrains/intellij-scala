package org.jetbrains.plugins.scala.inferAst

trait ElementAstAction
object ElementAstAction {
  case object Start extends ElementAstAction
  case class Token(token: String) extends ElementAstAction
  case class SubElement(element: String) extends ElementAstAction
  case object Exit extends ElementAstAction
}


object ElementAst {
  def from(raw: AstAutomaton[AstAction]): Map[String, AstAutomaton[ElementAstAction]] = {



    ???
  }
}
