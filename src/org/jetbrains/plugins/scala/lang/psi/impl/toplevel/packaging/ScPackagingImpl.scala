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
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 20.02.2008
* Time: 18:20:16
* To change this template use File | Settings | File Templates.
*/

class ScPackagingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackaging{
  override def toString = "ScPackaging"

  /**
  *
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

  def getTypeDefs = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_TYPE_BIT_SET)

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


  def getInnerPackagings: Iterable[ScPackaging] = childrenOfType[ScPackaging](ScalaElementTypes.PACKAGING_BIT_SET)

  //[NotNull]
  //def getFullPackageName: String = getChild(ScalaElementTypes.QUAL_ID).asInstanceOf[ScQualId].getFullName

}
