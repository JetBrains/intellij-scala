package org.jetbrains.plugins.scala.lang.psi.impl.base


import api.expr.ScAnnotations
import com.intellij.psi.javadoc.PsiDocComment
import java.lang.String
import java.util.{ArrayList, List}
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl





import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._

/**
 * @author Alexander Podkhalyuzin
 * Date: 07.03.2008
 */

class ScPrimaryConstructorImpl(node: ASTNode) extends ScMemberImpl(node) with ScPrimaryConstructor {
  override def hasAnnotation: Boolean = {
    return !(getNode.getFirstChildNode.getFirstChildNode == null)
  }


  override def hasModifierProperty(name: String) = {
    name match {
      case "private" => {
        accessModifier match {
          case Some(x: ScAccessModifier) => x.isPrivate
          case None => false
        }
      }
      case "protected" => {
        accessModifier match {
          case Some(x: ScAccessModifier) => x.isProtected
          case None => false
        }
      }
      case "public" => accessModifier == None
      case _ => false
    }
  }

  //todo rewrite me!
  override def hasModifier: Boolean = false

  def getClassNameText: String = {
    return getNode.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].getName
  }

  override def toString: String = "PrimaryConstructor"


  lazy val getFakePsiMethod: PsiMethod = {
    new ScalaPsiElementImpl(getNode) with PsiMethod {
      def setName(name: String): PsiElement = null

      def getContainingClass: PsiClass = ScPrimaryConstructorImpl.this.getContainingClass

      def hasModifierProperty(name: String): Boolean = ScPrimaryConstructorImpl.this.hasModifierProperty(name)

      def getModifierList: PsiModifierList = new ScModifierListImpl(getNode)

      def getReturnTypeElement = null

      def getHierarchicalMethodSignature = null

      def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

      def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

      def findSuperMethods = PsiMethod.EMPTY_ARRAY

      def findDeepestSuperMethod = null

      def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

      def getPom = null

      def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
        new ArrayList[MethodSignatureBackedByPsiMethod]()

      def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

      def getTypeParameters = PsiTypeParameter.EMPTY_ARRAY

      //todo implement me!
      def isVarArgs = false

      def isConstructor = true

      def getBody = null

      def getThrowsList = findChildByClass(classOf[ScAnnotations])

      def getTypeParameterList = null

      def hasTypeParameters = false

      def getParameterList: ScParameters = ScPrimaryConstructorImpl.this.parameterList

      def getNameIdentifier: PsiIdentifier = null

      def getReturnType: PsiType = PsiType.VOID

      def getDocComment: PsiDocComment = null

      def isDeprecated: Boolean = false
    }
  }
}