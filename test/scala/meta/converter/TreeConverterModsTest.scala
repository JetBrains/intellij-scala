package scala.meta.converter

import scala.meta.TreeConverterTestBaseNoLibrary
import scala.meta._

class TreeConverterModsTest extends TreeConverterTestBaseNoLibrary {

def testPrivateUnqual() {
  doTest(
    "private val a = 42",
     Defn.Val(List(Mod.Private(Name.Anonymous())), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
  )
}
  
  def testPrivateThis() {
    doTest(
      "private[this] val a = 42",
      Defn.Val(List(Mod.Private(Term.This(Name.Anonymous()))), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
    )
  }
  
  def testPrivatePackage() {
    doTest(
      "private[scala] val a = 42",
      Defn.Val(List(Mod.Private(Name.Indeterminate("scala"))), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
    )
  }

  def testProtectedUnqual() {
    doTest(
      "protected val a = 42",
      Defn.Val(List(Mod.Protected(Name.Anonymous())), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
    )
  }

  def testProtectedThis() {
    doTest(
      "protected[this] val a = 42",
      Defn.Val(List(Mod.Protected(Term.This(Name.Anonymous()))), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
    )
  }

  def testProtectedPackage() {
    doTest(
      "protected[scala] val a = 42",
      Defn.Val(List(Mod.Protected(Name.Indeterminate("scala"))), List(Pat.Var.Term(Term.Name("a"))), None, Lit(42))
    )
  }

}
