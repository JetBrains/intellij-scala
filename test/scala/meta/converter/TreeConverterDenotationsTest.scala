package scala.meta.converter

import scala.meta._
import scala.meta.prettyprinters.Structure

class TreeConverterDenotationsTest extends TreeConverterTestBaseWithLibrary {

  def doTest(text: String, expected: String) = {
    implicit val c = semanticContext
    val converted = convert(text)
    val got = converted.show[Structure].trim.replaceAll("local#.+\\n", "(LOCAL)\n")
    org.junit.Assert.assertEquals(expected.trim, got)
  }

  // FIXME: disabled until semantics engine is implemented
  def testListApply() {
    return
    doTest(
      "scala.collection.immutable.List(42)",
      """
        |Term.Apply(Term.Select(Term.Select(Term.Select(Term.Name("scala")[1]{1}<>, Term.Name("collection")[2]{2}<>){2}<>, Term.Name("immutable")[3]{3}<>){3}<>, Term.Name("List")[4]{4}<1>){5}<>, Seq(Lit.Int(42){6}<>)){7}<>
        |[1] {8}::scala
        |[2] {1}::scala.collection
        |[3] {2}::scala.collection.immutable
        |[4] {3}::scala.collection.immutable#List
        |[5] {10}::scala.collection.immutable#List.apply(Lscala/collection/Seq;)Lscala/collection/immutable/List;
        |[6] {1}::scala#Function1
        |[7] {2}::scala.collection#Seq
        |[8] {1}::scala#Nothing
        |[9] {1}::scala#Int
        |[10] {0}::_root_
        |[11] {0}::(LOCAL)
        |{1} Type.Singleton(Term.Name("scala")[1]{1}<>)
        |{2} Type.Singleton(Term.Name("collection")[2]{2}<>)
        |{3} Type.Singleton(Term.Name("immutable")[3]{3}<>)
        |{4} Type.Singleton(Term.Name("List")[4]{4}<1>)
        |{5} Type.Apply(Type.Name("Function1")[6], Seq(Type.Apply(Type.Name("Seq")[7], Seq(Type.Name("Nothing")[8])), Type.Apply(Type.Name("List")[4], Seq(Type.Name("Nothing")[8]))))
        |{6} Type.Name("Int")[9]
        |{7} Type.Apply(Type.Name("List")[4], Seq(Type.Name("Int")[9]))
        |{8} Type.Singleton(Term.Name("_root_")[10]{8}<>)
        |{9} Type.Method(Seq(Seq(Term.Param(Nil, Term.Name("xs")[11]{6}<>, Some(Type.Arg.Repeated(Type.Name("Int")[9])), None){6})), Type.Apply(Type.Name("Function1")[6], Seq(Type.Name("Int")[9], Type.Apply(Type.Name("List")[4], Seq(Type.Name("Int")[9])))))
        |{10} Type.Singleton(Term.Name("List")[4]{10}<>)
        |<1> Term.Name("apply")[5]{9}<>
      """.stripMargin
    )
  }

  // FIXME: disabled until semantics engine is implemented
  def testMapApply(): Unit = {
    return
    doTest(
    """
      |import scala.collection.mutable.Map
      |
      |//start
      |val a = Map(1->2, 2->3)
    """.stripMargin,
    """
      |Defn.Val(Nil, List(Pat.Var.Term(Term.Name("a")[1])), None, Term.Apply(Term.Name("GenMapFactory")[2], List(Term.ApplyInfix(Lit.Int(1), Term.Name("->")[3], Nil, List(Lit.Int(2))), Term.ApplyInfix(Lit.Int(2), Term.Name("->")[4], Nil, List(Lit.Int(3))))))
      |[1] Type.Singleton(Term.Name("_root_")[5])::local#temp:///src/aaa.scala*50
      |[2] Type.Apply(Type.Name("GenMapFactory")[6], List(Type.Name("Map")[7]))::scala.collection.generic#GenMapFactory.apply(Lscala/collection/Seq;)Ljava/lang/Object;
      |[3] Type.Apply(Type.Select(Term.Name("Predef")[8], Type.Name("ArrowAssoc")[9]), List(Type.Name("Int")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;Ljava/lang/Object;)Lscala/Tuple2;
      |[4] Type.Apply(Type.Select(Term.Name("Predef")[8], Type.Name("ArrowAssoc")[9]), List(Type.Name("Int")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;Ljava/lang/Object;)Lscala/Tuple2;
      |[5] 0::_root_
      |[6] Type.Singleton(Term.Name("generic")[10])::scala.collection.generic#GenMapFactory
      |[7] Type.Singleton(Term.Name("mutable")[11])::scala.collection.mutable#Map
      |[8] Type.Singleton(Term.Name("scala")[12])::scala#Predef
      |[9] Type.Name("Predef")[8]::scala.Predef#ArrowAssoc
      |[10] Type.Singleton(Term.Name("collection")[13])::scala.collection.generic
      |[11] Type.Singleton(Term.Name("collection")[13])::scala.collection.mutable
      |[12] Type.Singleton(Term.Name("_root_")[5])::scala
      |[13] Type.Singleton(Term.Name("scala")[12])::scala.collection
    """.stripMargin
    )
  }

  // FIXME: disabled until semantics engine is implemented
  def testBackwardLookup(): Unit = {
    return
    import scala.meta.internal.{ast => m, semantic => h}
    val tree = convert(
      """
        |java.lang.System.exit(1)
      """.stripMargin)
    tree match {
      case Term.Apply(fun, args) => fun match {
        case Term.Select(qual, name) => semanticContext.fromSymbol(name.denot.symbols.head)
      }
    }
  }

}
