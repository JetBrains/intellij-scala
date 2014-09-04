package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypingContext}

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
    if (plist != null) {
      val patterns = plist.patterns
      if (patterns.length == 1) {
        patterns(0).bindings
      } else patterns.flatMap((p: ScPattern) => p.bindings)
    } else Seq.empty
  }

  def declaredElements = bindings

  def getType(ctx: TypingContext) = {
    typeElement match {
      case Some(te) => te.getType(ctx)
      case None => expr.map(_.getType(ctx)).getOrElse(Failure("Cannot infer type without an expression", Some(this)))
    }
  }

  def expr: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScValueStub].getBodyExpr.orElse(Option(findChildByClassScala(classOf[ScExpression])))
    }
    Option(findChildByClassScala(classOf[ScExpression]))
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