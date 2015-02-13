package org.jetbrains.plugins.scala.meta

import com.intellij.psi.{PsiFileFactory, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.meta.trees.TreeAdapter

import scala.meta.internal.ast._


class TreeConverterTest extends SimpleTestCase {

  implicit def psiFromText(text: String): ScalaPsiElement = {
    val manager = PsiManager.getInstance(fixture.getProject)
    val dummyFile: ScalaFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText("DUMMY" + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
        ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScalaPsiElement]
  }

  def treesEq(a: Tree, b: Tree): Boolean = {
    def seqEq(t1: Seq[Tree], t2: Seq[Tree]): Boolean = {
      t1.zip(t2).forall { case(st1, st2) => treesEq(st1, st2) }
    }
    val ai = a.productIterator
    val bi = b.productIterator
    (a.getClass == b.getClass) && ai.zip(bi).forall {
      case (t1: Tree, t2: Tree) => treesEq(t1, t2)
      case (Some(st1:Tree), Some(st2:Tree)) => treesEq(st1, st2)
      case (t1: Seq[Tree], t2: Seq[Tree])   => seqEq(t1, t2)
      case (t1: Seq[Seq[Tree]], t2: Seq[Seq[Tree]]) =>
        t1.zip(t2).forall { case(st1, st2) => seqEq(st1, st2) }
      case (prim1, prim2)                   => prim1.equals(prim2)
    }
  }
  
  def doTest(text: String, tree: Tree) = {
    val converted = TreeAdapter.ideaToMeta(text)
    assert(treesEq(converted, tree), s"$converted <=> $tree")
  }

  def testVal() {
    doTest(
      "val x,y: Int",
      Decl.Val(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testVar() {
    doTest(
      "var x: Int",
      Decl.Var(Nil, List(Pat.Var.Term(Term.Name("x"))), Type.Name("Int"))
    )
  }


  def testMultiVal() {
    doTest(
    "val x, y: Int",
      Decl.Val(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testMultiVar() {
    doTest(
      "var x, y: Int",
      Decl.Var(Nil, List(Pat.Var.Term(Term.Name("x")), Pat.Var.Term(Term.Name("y"))), Type.Name("Int"))
    )
  }

  def testTypeT(): Unit = {
    doTest(
      "type T",
      Decl.Type(Nil, Type.Name("T"), Nil, Type.Bounds(None, None))
    )
  }

}
