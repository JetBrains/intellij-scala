package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScTmplDef
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:13:02
 */

  class ScFile ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "file"

    def getPackaging : Iterable[ScPackaging] = childrenOfType[ScPackaging] (ScalaElementTypes.PACKAGING_BIT_SET)

    def getTmplDefs : List[ScTmplDef] = {
      val children = childrenOfType[ScalaPsiElementImpl] (ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)

      (children :\ (Nil : List[ScTmplDef])) (
      (y : ScalaPsiElementImpl, x : List[ScTmplDef]) => y.getNode.getElementType match
        {
          case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
          case _ => (y.asInstanceOf[ScTmplDef]) :: x
        }
      )
    }
  }

  class ScCompilationUnit ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Compilation unit"
  }

  class ScPackaging ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Packaging"

    def getTmplDefs : Iterable[ScTmplDef] = childrenOfType[ScTmplDef] (ScalaElementTypes.TMPL_DEF_BIT_SET)

    def getFullPackageName : String = {
      val qualId = getChild[ScQualId]
      if (qualId == null) null else qualId.getFullName
    }
  }

  class ScPackagingBlock ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Packaging block"        
  }

  class ScPackageStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Package statement"

    //nullable
    def getFullPackageName : String = {
      val qualId = getChild[ScQualId]
      if (qualId == null) null else qualId.getFullName
    }
  }

  case class ScQualId ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = "Qualified identifier"

    //todo: change stableId parsing to qualId parsing
    def getFullName = getText
  }
}