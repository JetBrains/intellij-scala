package org.jetbrains.plugins.scala.meta
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.meta.trees.ConverterImpl

import scala.meta.internal.ast.Tree

trait TreeConverterTestBase extends SimpleTestCase {

  implicit def psiFromText(text: String): ScalaPsiElement = {
    val file: ScalaFile = parseText(text + "\n42") //HACK: force file to be a scala script to ease resolving
    val startPos = file.getText.indexOf("//start")
    if (startPos < 0)
      file.firstChild.get.asInstanceOf[ScalaPsiElement]
    else
      file.findElementAt(startPos).getParent.asInstanceOf[ScalaPsiElement]
  }

  def structuralEquals(tree1: Tree, tree2: Tree): Boolean = {
    // NOTE: for an exhaustive list of tree field types see
    // see /foundation/src/main/scala/org/scalameta/ast/internal.scala
    def loop(x1: Any, x2: Any): Boolean = (x1, x2) match {
      case (x1: Tree, x2: Tree) => structuralEquals(x1, x2)
      case (Some(x1), Some(x2)) => loop(x1, x2)
      case (Seq(xs1@_*), Seq(xs2@_*)) => xs1.zip(xs2).forall { case (x1, x2) => loop(x1, x2)}
      case (x1, x2) => x1 == x2
    }
    def tagsEqual = tree1.internalTag == tree2.internalTag
    def fieldsEqual = tree1.productIterator.toList.zip(tree2.productIterator.toList).forall { case (x1, x2) => loop(x1, x2)}
    (tagsEqual && fieldsEqual) || {println(s"${tree1.show[scala.meta.Raw]} <=> ${tree2.show[scala.meta.Raw]}"); false}
  }

  def doTest(text: String, tree: Tree) = {
    val converted = convert(text)
    assert(structuralEquals(converted, tree), s"$converted <=> $tree")
    assert(converted.toString() == tree.toString(), s"TEXT: $converted <=> $tree")
  }

  protected def convert(text: String): Tree = {
    ConverterImpl.ideaToMeta(text)
  }
}
