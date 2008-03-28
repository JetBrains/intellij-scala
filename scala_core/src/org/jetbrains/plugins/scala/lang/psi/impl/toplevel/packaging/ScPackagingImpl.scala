package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScPackagingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackaging{

  override def toString = "ScPackaging"

  [NotNull]
  def getFullPackageName: String = findChildByClass(classOf[ScReferenceElement]).getText


  /**
  * @retruns Template definitions inside current packaging
  */
 /* def getTmplDefs: Iterable[ScTmplDef] = {
    val children = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTmplDef]))((y: ScalaPsiElementImpl, x: List[ScTmplDef]) =>
      y.getNode.getElementType match
      {
        case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
        case _ => y.asInstanceOf[ScTmplDef] :: y.asInstanceOf[ScTmplDef].getTmplDefs.toList ::: x
      })

  }  */

  def getTypeDefs = childrenOfType[ScalaPsiElementImpl](TokenSets.TMPL_OR_TYPE_BIT_SET)

/*
  override def processDeclarations(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor,
          lastParent: PsiElement,
          place: PsiElement) : Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve.processors._

    if (processor.isInstanceOf[ScalaClassResolveProcessor]) {
      this.canBeObject = processor.asInstanceOf[ScalaClassResolveProcessor].canBeObject
      this.offset = processor.asInstanceOf[ScalaClassResolveProcessor].offset
      getClazz(getTypeDefs, processor, substitutor)
    } else true
  }
*/


  def getInnerPackagings: Iterable[ScPackaging] = childrenOfType[ScPackaging](TokenSets.PACKAGING_BIT_SET)

}
