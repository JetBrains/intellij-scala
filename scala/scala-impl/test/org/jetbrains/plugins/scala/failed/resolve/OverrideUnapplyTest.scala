package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
  * @author mucianm 
  * @since 25.03.16.
  */
class OverrideUnapplyTest extends SimpleTestCase {

  override protected def shouldPass: Boolean = false

  private val CARET = "<caret>"

  def testSCL2676(): Unit = {
    val m =
      """
        |case class Test[T](a: T, b: Int)
        |
        |object Test {
        |  def unapply(str: String): Option[(String, Int)] = Some((str, 0))
        |
        |  def test[T](obj: Test[T]) = {
        |    val <caret>Test(a, b) = obj
        |    (a, b)
        |  }
        |}
      """
    val trimmed = m.trim
    val pos = trimmed.indexOf(CARET)
    val psi = trimmed.replaceAll(CARET, "").parse
    val expr = psi.findElementAt(pos) match {
      case e: LeafPsiElement =>
        Some(e.getParent.asInstanceOf[ScStableCodeReference].resolve())
      case other =>
        None
    }
    expr match {
      case Some(f: ScFunctionDefinition) =>
        assert(shouldPass ^ f.isSynthetic, "Unapply doesn't resolve to overriding method")
      case _ => assert(false)
    }
  }


}
