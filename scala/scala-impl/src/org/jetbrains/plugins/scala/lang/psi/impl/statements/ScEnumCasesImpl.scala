package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScEnumCasesElementType

final class ScEnumCasesImpl(
  stub: ScEnumCasesStub,
  nodeType: ScEnumCasesElementType.type,
  node: ASTNode
) extends ScalaStubBasedElementImpl(stub, nodeType, node)
  with ScEnumCases {

  override def toString: String =
    "ScEnumCases" + ifReadAllowed(declaredNames.mkString(": ", ", ", ""))("")

  override def declaredElements: Seq[ScEnumCase] =
    byPsiOrStub(findChildrenByClassScala(classOf[ScEnumCase]))(_.getChildrenByType(TokenSets.ENUM_CASES, new Array[ScEnumCase](_))).toSeq

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitEnumCases(this)

  override def getContainingClass: PsiClass =
    PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      .ensuring(_.is[ScEnum])
}
