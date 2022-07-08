package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.FIELD_ID
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypedDeclaration, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScImportableDeclarationsOwner
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFieldIdStub
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScFieldIdImpl private(stub: ScFieldIdStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, FIELD_ID, node) with ScFieldId with ScImportableDeclarationsOwner {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScFieldIdStub) = this(stub, null)

  override def toString: String = "Field identifier: " + ifReadAllowed(name)("")

  override def `type`(): TypeResult = getParent /*id list*/ .getParent match {
    case typed: ScTypedDeclaration => typed.`type`()
    //partial matching
  }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def isStable: Boolean = getContext match {
    case l: ScIdList => l.getContext match {
      case v: ScVariable => v.isStable // it should be just ScVariableDeclaration (abstract var)
      case _ => true
    }
    case _ => true
  }

  override def delete(): Unit = {
    getContext match {
      case id: ScIdList if id.fieldIds == Seq(this) =>
        id.getContext.delete()
      case _ => throw new UnsupportedOperationException("Cannot delete on id in a list of field ides.")
    }
  }

  override def isVar: Boolean = nameContext.isInstanceOf[ScVariable]

  override def isVal: Boolean = nameContext.isInstanceOf[ScValue]
}