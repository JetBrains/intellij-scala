trait Analyzer extends Typers{
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

abstract class D {
  val global: Global

  import global._
  val runDefinitions = currentRun.runDefinitions
  import runDefinitions.{Predef_???, x}

  /*start*/Predef_???/*end*/
  x
}
//D.this.global.TermSymbol