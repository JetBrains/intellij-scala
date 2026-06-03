package scala.meta.converter

import scala.meta.{TreeConverterTestBaseNoLibrary, _}

class TreeConverterModsTest extends TreeConverterTestBaseNoLibrary {

def testPrivateUnqual(): Unit = {
  doTest(
    "private val a = 42",
     Defn.Val(List(Mod.Private(Name.Anonymous())), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
  )
}
  
  def testPrivateThis(): Unit = {
    doTest(
      "private[this] val a = 42",
      Defn.Val(List(Mod.Private(Term.This(Name.Anonymous()))), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
    )
  }
  
  def testPrivatePackage(): Unit = {
    doTest(
      "private[scala] val a = 42",
      Defn.Val(List(Mod.Private(Name.Indeterminate("scala"))), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
    )
  }

  def testProtectedUnqual(): Unit = {
    doTest(
      "protected val a = 42",
      Defn.Val(List(Mod.Protected(Name.Anonymous())), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
    )
  }

  def testProtectedThis(): Unit = {
    doTest(
      "protected[this] val a = 42",
      Defn.Val(List(Mod.Protected(Term.This(Name.Anonymous()))), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
    )
  }

  def testProtectedPackage(): Unit = {
    doTest(
      "protected[scala] val a = 42",
      Defn.Val(List(Mod.Protected(Name.Indeterminate("scala"))), List(Pat.Var(Term.Name("a"))), None, Lit.Int(42))
    )
  }
}
