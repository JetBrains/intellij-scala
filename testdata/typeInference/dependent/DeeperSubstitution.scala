trait Analyzer extends Typers with Infer {
  val global: Global


}
trait Definitions { self: SymbolTable =>
  object definitions extends DefinitionsClass

  def memb: TermSymbol = null

  class DefinitionsClass {
    def Predef_??? = memb

    class RunDefinitions {
      lazy val Predef_??? = DefinitionsClass.this.Predef_???

      val x = 123
    }
  }
}

abstract class SymbolTable extends Symbols with Definitions {

}

trait Symbols { self: SymbolTable =>
  class TermSymbol
  class Symbol
}

trait Typers {
  self: Analyzer =>
  sealed class B[+T]

  case class A[+T](value: T) extends B[T]
}

class Global extends SymbolTable {
  lazy val analyzer = new {val global: Global.this.type = Global.this} with Analyzer

  def currentRun: Run = null

  class Run {
    val runDefinitions: definitions.RunDefinitions = null
  }
}

trait Infer {
  self: Analyzer =>

  import global._

  def freshVar(s: Symbol): Int = 123
}

trait Validators {
  self: DefaultMacroCompiler =>

  import global._
  import analyzer._

  trait Validator {
    self: MacroImplRefCompiler =>
    lazy val atparams : List[global.Symbol] = null
    atparams.map(tparam => freshVar(/*start*/tparam/*end*/))
  }
}


abstract class DefaultMacroCompiler extends Validators{
  val global: Global

  import global._
  val runDefinitions = currentRun.runDefinitions
  import runDefinitions.{Predef_???, x}

  class MacroImplRefCompiler extends Validator

  Predef_???
  x
}
//Validators.this.global.Symbol