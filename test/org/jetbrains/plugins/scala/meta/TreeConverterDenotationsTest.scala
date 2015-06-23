package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._
import scala.meta._

class TreeConverterDenotationsTest extends TreeConverterTestBaseWithLibrary {

  def doTest(text: String, expected: String) = {
    val converted = convert(text)
    val got = converted.show[Semantics]
    assert(got == expected, s"$got\n<=>\n$expected")
  }

  def testListApply() {
    doTest(
      "scala.collection.immutable.List()",
      """Term.Apply(Term.Select(Term.Select(Term.Select(Term.Name("scala")[1], Term.Name("collection")[2]), Term.Name("immutable")[3]), Term.Name("List")[4]), Nil)
        |[1] Type.Singleton(Term.Name("_root_")[5])::scala
        |[2] Type.Singleton(Term.Name("scala")[1])::scala.collection
        |[3] Type.Singleton(Term.Name("collection")[2])::scala.collection.immutable
        |[4] Type.Name("_root_.scala.collection.immutable.List.type")[6]::scala.collection.immutable#List.apply(Lscala/collection/Seq;)Lscala/collection/immutable/List;
        |[5] 0::_root_
        |[6] 0::scala.collection.immutable#List""".stripMargin
    )
  }

  def testMapApply(): Unit = {
    doTest(
    """
      |import scala.collection.mutable.Map
      |
      |//start
      |val a = Map(1->2, 2->3)
    """.stripMargin,
    """Defn.Val(Nil, List(Pat.Var.Term(Term.Name("a")[1])), None, Term.Apply(Term.Name("GenMapFactory")[2], List(Term.ApplyInfix(Lit.Int(1), Term.Name("->")[3], Nil, List(Lit.Int(2))), Term.ApplyInfix(Lit.Int(2), Term.Name("->")[4], Nil, List(Lit.Int(3))))))
      |[1] 0::local#fooscala:50
      |[2] Type.Apply(Type.Name("_root_.scala.collection.generic.GenMapFactory")[5], List(Type.Name("CC")))::scala.collection.generic#GenMapFactory.apply(Lscala/collection/Seq;)Ljava/lang/Object;
      |[3] Type.Apply(Type.Name("_root_.scala.Predef.ArrowAssoc"), List(Type.Name("A")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;)Lscala/Tuple2;
      |[4] Type.Apply(Type.Name("_root_.scala.Predef.ArrowAssoc"), List(Type.Name("A")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;)Lscala/Tuple2;
      |[5] 0::scala.collection.generic#GenMapFactory"""stripMargin
    )
  }

}
