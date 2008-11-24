package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.meta.PsiMetaData
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


import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScAnnotation{
  override def toString: String = "Annotation"

  def getMetaData: PsiMetaData = null

  def getParameterList: PsiAnnotationParameterList = null

  def getQualifiedName: String = ScType.extractClassType(annotationExpr.constr.typeElement.getType) match {
    case None => null
    case Some((c: PsiClass, _)) => c.getQualifiedName
  }

  def findDeclaredAttributeValue(attributeName: String): PsiAnnotationMemberValue = null

  def findAttributeValue(attributeName: String): PsiAnnotationMemberValue = null

  def getNameReferenceElement: PsiJavaCodeReferenceElement = null
}