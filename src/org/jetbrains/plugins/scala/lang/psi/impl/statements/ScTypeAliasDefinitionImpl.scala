package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import stubs.ScTypeAliasStub;
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import api.ScalaElementVisitor
import psi.types.{ScTypeParameterType, ScType, Equivalence, ScParameterizedType}
import api.toplevel.ScTypeParametersOwner
import api.statements.params.ScTypeParam
import api.toplevel.typedef.{ScMember, ScObject, ScTrait, ScClass}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:13
*/
class ScTypeAliasDefinitionImpl extends ScalaStubBasedElementImpl[ScTypeAlias] with ScTypeAliasDefinition {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeAliasStub) = {this(); setStub(stub); setNode(null)}

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScTypeAliasStub].getName, getManager).getPsi
    case n => n
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def navigate(requestFocus: Boolean) {
    val descriptor =  EditSourceUtil.getDescriptor(nameId);
    if (descriptor != null) descriptor.navigate(requestFocus)
  }

  override def toString: String = "ScTypeAliasDefinition: " + name

  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = "(" + ScTypeAliasDefinitionImpl.this.containingClass.qualifiedName + ")"
      override def getIcon(open: Boolean) = ScTypeAliasDefinitionImpl.this.getIcon(0)
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDefinition].getOriginalElement

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeAliasDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTypeAliasDefinition(this)
      case _ => super.accept(visitor)
    }
  }

  def isExactAliasFor(cls: PsiClass): Boolean = {
    val isDefinedInObject = containingClass match {
      case obj: ScObject if obj.isStatic => true
      case _ => false
    }
    if (isDefinedInObject) {
      if (cls.getTypeParameters.length != typeParameters.length) {
        return false
      } else if (cls.hasTypeParameters) {
        val typeParamsAreAppliedInOrderToCorrectClass = aliasedType.getOrAny match {
          case pte: ScParameterizedType =>
            val refersToClass = Equivalence.equiv(pte.designator, ScType.designator(cls))
            val typeParamsAppliedInOrder = (pte.typeArgs corresponds typeParameters) {
              case (tpt: ScTypeParameterType, tp) if tpt.param == tp => true
              case _ => false
            }
            refersToClass && typeParamsAppliedInOrder
          case _ => false
        }
        val varianceAndBoundsMatch = cls match {
          case sc0@(_: ScClass | _: ScTrait) =>
            val sc = sc0.asInstanceOf[ScTypeParametersOwner]
            (typeParameters corresponds sc.typeParameters) {
              case (tp1, tp2) => tp1.variance == tp2.variance && tp1.upperBound == tp2.upperBound && tp1.lowerBound == tp2.lowerBound &&
                      tp1.contextBound.isEmpty && tp2.contextBound.isEmpty && tp1.viewBound.isEmpty && tp2.viewBound.isEmpty
            }
          case _ => // Java class
            (typeParameters corresponds cls.getTypeParameters) {
              case (tp1, tp2) => tp1.variance == ScTypeParam.Invariant && tp1.upperTypeElement.isEmpty && tp2.getExtendsListTypes.isEmpty &&
                      tp1.lowerTypeElement.isEmpty && tp1.contextBound.isEmpty && tp1.viewBound.isEmpty
            }
        }
        return typeParamsAreAppliedInOrderToCorrectClass && varianceAndBoundsMatch
      } else {
        val clsType = ScType.designator(cls)
        return typeParameters.isEmpty && Equivalence.equiv(aliasedType.getOrElse(return false), clsType)
      }
    }

    false
  }
}
