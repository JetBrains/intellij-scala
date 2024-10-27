package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.TYPE_DECLARATION
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result._

import javax.swing.Icon

final class ScTypeAliasDeclarationImpl private(stub: ScTypeAliasStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, TYPE_DECLARATION, node) with ScTypeAliasDeclaration {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTypeAliasStub) = this(stub, null)

  override def navigate(requestFocus: Boolean): Unit = {
    val descriptor = EditSourceUtil.getDescriptor(this)
    if (descriptor != null) {
      descriptor.navigate(requestFocus)
    }
  }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER) match {
    case null => createIdentifier(getGreenStub.getName).getPsi
    case n => n
  }

  override def toString: String = "ScTypeAliasDeclaration: " + ifReadAllowed(name)("")

  override def lowerBound: TypeResult = lowerTypeElement match {
    case Some(te) => te.`type`()
    case None => Right(Nothing)
  }

  override def upperBound: TypeResult = upperTypeElement match {
    case Some(te) => te.`type`()
    case None => Right(Any)
  }

  override def contextBounds: Seq[ScContextBound] = findChildren[ScContextBound]

  override def upperTypeElement: Option[ScTypeElement] =
    byPsiOrStub(boundElement(tUPPER_BOUND))(_.upperBoundTypeElement)

  override def lowerTypeElement: Option[ScTypeElement] =
    byPsiOrStub(boundElement(tLOWER_BOUND))(_.lowerBoundTypeElement)

  private def boundElement(elementType: IElementType): Option[ScTypeElement] = {
    findLastChildByTypeScala[PsiElement](elementType).flatMap { element =>
      getNextSiblingOfType(element, classOf[ScTypeElement]).toOption
    }
  }

  override protected def baseIcon: Icon = Icons.ABSTRACT_TYPE_ALIAS

  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      override def getPresentableText: String = name
      override def getLocationString: String = {
        val containingClass = ScTypeAliasDeclarationImpl.this.containingClass
        val qname = if (containingClass != null) containingClass.qualifiedName else ""
        "(" + qname + ")"
      }
      override def getIcon(open: Boolean): Icon = ScTypeAliasDeclarationImpl.this.getIcon(0)
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDeclaration].getOriginalElement

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypeAliasDeclaration(this)
  }

  override def isEffectivelyFinal: Boolean = false
}