package org.jetbrains.plugins.scala.lang.psi.impl {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.TmplDef
import org.jetbrains.plugins.scala.lang.psi.impl.patterns
import com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:13:02
 */

  class ScFile ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "file"

    def getPackaging : Array[ScPackaging] = {
      for (val child <- getChildren; child.isInstanceOf[ScPackaging]) yield child.asInstanceOf[ScPackaging] 
    }
  }

  class ScCompilationUnit ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Compilation unit"
  }

  class ScPackaging ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Packaging"

    def getTmplDefs : Seq[TmplDef] = {
      for (val tmplDef <- getChildren; tmplDef.isInstanceOf[TmplDef]) yield tmplDef.asInstanceOf[TmplDef]
    }
  }

  class ScPackageStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Package statement"

    //nullable
    def getPackageName : ScQualId = {
      // package(0) a.b.c.d(1)
      val children = getChildren
      if (children(1).isInstanceOf[ScQualId]) children(1).asInstanceOf[ScQualId]
      else null
    }
  }

  case class ScQualId ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = "Qualified identifier"

    def getQualId = getText
  }
}