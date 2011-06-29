package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import com.intellij.lang.ASTNode
import stubs.{ScValueStub}

import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.Any
import psi.types.result.TypingContext
import api.expr.ScExpression
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/** 
* @author Alexander Podkhalyuzin
*/

class ScPatternDefinitionImpl extends ScalaStubBasedElementImpl[ScValue] with ScPatternDefinition {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {
    this()
    setNode(node)
  }

  def this(stub: ScValueStub) = {
    this()
    setStub(stub)
    setNode(null)
  }
  
  override def toString: String = "ScPatternDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def declaredElements = bindings

  def getType(ctx: TypingContext) = {
    typeElement match {
      case Some(te) => te.getType(ctx)
      case None => expr.getType(ctx)
    }
  }

  def expr: ScExpression = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScValueStub].getBodyExpr.getOrElse(findChildByClassScala(classOf[ScExpression]))
    }
    findChildByClassScala(classOf[ScExpression])
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScValueStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PATTERN_LIST, JavaArrayFactoryUtil.ScPatternListFactory).apply(0)
    } else findChildByClass(classOf[ScPatternList])
  }
}