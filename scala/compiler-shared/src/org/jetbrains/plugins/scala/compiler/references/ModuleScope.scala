package org.jetbrains.plugins.scala.compiler.references

sealed trait ModuleScope {
  val suffix: String

  def appendScopeSuffix(name: String): String = name + suffix
}

object ModuleScope {

  case object Production extends ModuleScope {
    override val suffix: String = "_$production$"
  }

  case object Test extends ModuleScope {
    override val suffix: String = "_$test$"
  }

  def parse(string: String): Option[(String, ModuleScope)] = string match {
    case s if s.endsWith(Production.suffix) => Some(s.stripSuffix(Production.suffix), Production)
    case s if s.endsWith(Test.suffix) => Some(s.stripSuffix(Test.suffix), Test)
    case _ => None
  }
}
