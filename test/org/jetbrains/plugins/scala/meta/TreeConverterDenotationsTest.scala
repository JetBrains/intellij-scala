package org.jetbrains.plugins.scala.meta

import scala.meta._

class TreeConverterDenotationsTest extends TreeConverterTestBaseWithLibrary {

  def doTest(text: String, expected: String) = {
    val converted = convert(text)
    val got = converted.show[Semantics].trim
    assert(got == expected.trim, s"GOT:\n$got\nEXPECTED:\n$expected")
  }

  def testListApply() {
    doTest(
      "scala.collection.immutable.List()",
      """
        |Term.Apply(Term.Select(Term.Select(Term.Select(Term.Name("scala")[1], Term.Name("collection")[2]), Term.Name("immutable")[3]), Term.Name("List")[4]), Nil)
        |[1] Type.Singleton(Term.Name("_root_")[5])::scala
        |[2] Type.Singleton(Term.Name("scala")[1])::scala.collection
        |[3] Type.Singleton(Term.Name("collection")[2])::scala.collection.immutable
        |[4] Type.Name("List")[6]::scala.collection.immutable#List.apply(Lscala/collection/Seq;)Lscala/collection/immutable/List;
        |[5] 0::_root_
        |[6] Type.Singleton(Term.Name("immutable")[3])::scala.collection.immutable#List
      """.stripMargin
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
    """
      |Defn.Val(Nil, List(Pat.Var.Term(Term.Name("a")[1])), None, Term.Apply(Term.Name("GenMapFactory")[2], List(Term.ApplyInfix(Lit.Int(1), Term.Name("->")[3], Nil, List(Lit.Int(2))), Term.ApplyInfix(Lit.Int(2), Term.Name("->")[4], Nil, List(Lit.Int(3))))))
      |[1] Type.Singleton(Term.Name("_root_")[5])::local#fooscala:50
      |[2] Type.Apply(Type.Name("GenMapFactory")[6], List(Type.Name("Map")[7]))::scala.collection.generic#GenMapFactory.apply(Lscala/collection/Seq;)Ljava/lang/Object;
      |[3] Type.Apply(Type.Project(Type.Name("Predef")[8], Type.Name("ArrowAssoc")[9]), List(Type.Name("Int")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;Ljava/lang/Object;)Lscala/Tuple2;
      |[4] Type.Apply(Type.Project(Type.Name("Predef")[8], Type.Name("ArrowAssoc")[9]), List(Type.Name("Int")))::scala.Predef#ArrowAssoc.->(Ljava/lang/Object;Ljava/lang/Object;)Lscala/Tuple2;
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

}
