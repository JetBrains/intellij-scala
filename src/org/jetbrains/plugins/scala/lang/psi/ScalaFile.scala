package org.jetbrains.plugins.scala.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes


/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:53:33
 */
class ScalaFile(viewProvider: FileViewProvider)
extends PsiFileBase (viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
with ScalaPsiElement {

  override def getViewProvider() = {
    viewProvider
  }

  override def getFileType() = {
    ScalaFileType.SCALA_FILE_TYPE
  }

  override def toString: String = {
    "Scala file"
  }

  def getPackaging: Iterable[ScPackaging] = childrenOfType[ScPackaging](ScalaElementTypes.PACKAGING_BIT_SET)

  def getTmplDefs: List[ScTmplDef] = {
    val children = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTmplDef]))((y: ScalaPsiElementImpl, x: List[ScTmplDef]) => y.getNode.getElementType match
    {
      case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
      case _ => (y.asInstanceOf[ScTmplDef]) :: x
    })
  }

}
