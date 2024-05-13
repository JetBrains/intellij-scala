package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ifReadAllowed}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScTypeBoundsOwnerImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScTopLevelStubBasedElement
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

import javax.swing.Icon

final class ScTypeAliasDefinitionImpl private(stub: ScTypeAliasStub, node: ASTNode)
  extends ScalaStubBasedElementImpl[ScTypeAlias, ScTypeAliasStub](stub, ScalaElementType.TYPE_DEFINITION, node)
    with ScTopLevelStubBasedElement[ScTypeAlias, ScTypeAliasStub]
    with ScTypeAliasDefinition
    with ScTypeBoundsOwnerImpl {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTypeAliasStub) = this(stub, null)

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER) match {
    case null =>
      val name = getGreenStub.getName
      val id = createIdentifier(name)
      if (id == null) {
        throw new AssertionError(s"Id is null. Name: $name. Text: $getText. Parent text: ${getParent.getText}.")
      }
      id.getPsi
    case n => n
  }

  override def aliasedTypeElement: Option[ScTypeElement] =
    byPsiOrStub(findChild[ScTypeElement])(_.typeElement)

  override def lowerTypeElement: Option[ScTypeElement] = aliasedTypeElement
  override def upperTypeElement: Option[ScTypeElement] = aliasedTypeElement

  override def navigate(requestFocus: Boolean): Unit = {
    val descriptor =  EditSourceUtil.getDescriptor(this)
    if (descriptor != null) {
      descriptor.navigate(requestFocus)
    }
  }

  override def toString: String = "ScTypeAliasDefinition: " + ifReadAllowed(name)("")

  override protected def baseIcon: Icon = Icons.TYPE_ALIAS

  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      override def getPresentableText: String = name
      override def getIcon(open: Boolean): Icon = ScTypeAliasDefinitionImpl.this.getIcon(0)
      override def getLocationString: String = {
        val classFqn = ScTypeAliasDefinitionImpl.this.containingClass.toOption.map(_.qualifiedName)
        val fqn = classFqn.orElse(topLevelQualifier)
        "(" + fqn.getOrElse("") + ")"
      }
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDefinition].getOriginalElement

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypeAliasDefinition(this)
  }

  override def isEffectivelyFinal: Boolean = true
}
