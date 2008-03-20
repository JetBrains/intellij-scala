package org.jetbrains.plugins.scala.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._



class ScalaFile(viewProvider: FileViewProvider)
extends PsiFileBase (viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
with ScalaPsiElement {

  override def getViewProvider = viewProvider
  override def getFileType = ScalaFileType.SCALA_FILE_TYPE
  override def toString = "ScalaFile"

  def getPackaging: Iterable[ScPackaging] = childrenOfType[ScPackaging](ScalaElementTypes.PACKAGING_BIT_SET)

  /**
  *  Receiving al template definitions (such as class, object, trait) in current file
  */
  def getTmplDefs: List[ScTypeDefinition] = {
    Nil: List[ScTypeDefinition]
/*
    val children = childrenOfType[ScTypeDefinition](ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTypeDefinition]))((y: ScalaPsiElementImpl, x: List[ScTypeDefinition]) =>
      y.getNode.getElementType match
      {
//        case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
        case _ : ScTypeDefinition => y.asInstanceOf[ScTypeDefinition] :: (y.asInstanceOf[ScTypeDefinition]).getTmplDefs.toList ::: x
      })
*/
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
