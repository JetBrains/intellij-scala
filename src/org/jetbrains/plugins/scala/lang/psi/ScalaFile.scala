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
extends PsiFileBase (viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage()) {

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

  def childrenOfType[T >: Null <: ScalaPsiElementImpl](tokSet: TokenSet): Iterable[T] = new Iterable[T] () {
    def elements = new Iterator[T] () {
      private def findChild(child: ASTNode): ASTNode = child match {
        case null => null
        case _ =>
          if (tokSet.contains(child.getElementType())) child else findChild(child.getTreeNext)
      }

      val first = getFirstChild
      var n: ASTNode = if (first == null) null else findChild(first.getNode)

      def hasNext = n != null

      def next: T =  if (n == null) null else {
        val res = n
        n = findChild(n.getTreeNext)
        res.getPsi().asInstanceOf[T]
      }
    }
  }

  [Nullable]
  def getChild(elemType: IElementType): PsiElement = {
    def inner(e: PsiElement): PsiElement = e match {
      case null => null
      case _ => if (e.getNode.getElementType == elemType) e else inner(e.getNextSibling())
    }

    inner(getFirstChild())
  }
}
