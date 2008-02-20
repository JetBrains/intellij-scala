package org.jetbrains.plugins.scala.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.resolve.processors._
import org.jetbrains.plugins.scala.lang.psi.containers._



class ScalaFile(viewProvider: FileViewProvider)
extends PsiFileBase (viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
with ScalaPsiElement with Importable{

  override def getViewProvider = viewProvider
  override def getFileType = ScalaFileType.SCALA_FILE_TYPE
  override def toString = "ScalaFile"

  def getPackaging: Iterable[ScPackaging] = childrenOfType[ScPackaging](ScalaElementTypes.PACKAGING_BIT_SET)

  /**
  *  Receiving al template definitions (such as class, object, trait) in current file
  */
  def getTmplDefs: List[ScTmplDef] = {
    val children = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTmplDef]))((y: ScalaPsiElementImpl, x: List[ScTmplDef]) =>
      y.getNode.getElementType match
      {
        case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
        case _ => y.asInstanceOf[ScTmplDef] :: (y.asInstanceOf[ScTmplDef]).getTmplDefs.toList ::: x
      })
  }

  def getUpperDefs = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_DEF_BIT_SET)

/*
  override def processDeclarations(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor,
          lastParent: PsiElement,
          place: PsiElement): Boolean = {

    import org.jetbrains.plugins.scala.lang.resolve.processors._

    if (processor.isInstanceOf[ScalaClassResolveProcessor]) {
        this.canBeObject = processor.asInstanceOf[ScalaClassResolveProcessor].canBeObject
        this.offset = processor.asInstanceOf[ScalaClassResolveProcessor].offset
      getClazz(getUpperDefs, processor, substitutor)
    } else true
  }
*/


}
