package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScContextBoundStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScContextBoundElementType

class ScContextBoundImpl(@Nullable stub: ScContextBoundStub, nodeType: ScContextBoundElementType, @Nullable node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScContextBound {
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitContextBound(this)
  }

  override def typeElement: ScTypeElement = {
    byStubOrPsi(_.typeElement.get)(findChild[ScTypeElement].get)
  }

  override def nameIdOpt: Option[PsiElement] = {
    findLastChildByTypeScala(ScalaTokenTypes.tIDENTIFIER)

  }

  override def nameOpt: Option[String] =
    byStubOrPsi(stub => Option(stub.getName))(nameIdOpt.map(_.getText))

  override def nameId: PsiElement = nameIdOpt.getOrElse(findChild[ScTypeElement].orNull)

  override def toString: String = "ScContextBoundImpl(context bound)"
}
