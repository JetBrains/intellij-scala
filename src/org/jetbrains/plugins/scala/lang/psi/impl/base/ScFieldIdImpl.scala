package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.FIELD_ID
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypedDeclaration, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScImportableDeclarationsOwner
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFieldIdStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}

/**
  * @author ilyas
  */
class ScFieldIdImpl private(stub: StubElement[ScFieldId], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScFieldId with ScImportableDeclarationsOwner {

  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScFieldIdStub) =
    this(stub, FIELD_ID, null)

  override def toString: String = "Field identifier: " + name

  def getType(ctx: TypingContext): TypeResult[ScType] = getParent /*id list*/ .getParent match {
    case typed: ScTypedDeclaration => typed.getType(ctx)
    //partial matching
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def isStable: Boolean = getContext match {
    case l: ScIdList => l.getContext match {
      case _: ScVariable => false
      case _ => true
    }
    case _ => true
  }

  override def delete() {
    getContext match {
      case id: ScIdList if id.fieldIds == Seq(this) =>
        id.getContext.delete()
      case _ => throw new UnsupportedOperationException("Cannot delete on id in a list of field ides.")
    }
  }

  override def isVar: Boolean = nameContext.isInstanceOf[ScVariable]

  override def isVal: Boolean = nameContext.isInstanceOf[ScValue]
}