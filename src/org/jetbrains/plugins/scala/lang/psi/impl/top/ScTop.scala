package org.jetbrains.plugins.scala.lang.psi.impl {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:13:02
 */

  class ScFile ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "file"
  }

  class ScCompilationUnit ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "compilation unit"
  }

  class ScPackaging ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Packaging"

    /*def getPackageBlock : ScTopStatSeq = {
      return new ScTopStatSeq( node )
    } */
  }

  class ScPackageStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "package statement"
  }

  case class ScQualId ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = "Qualified identifier"
    def getQualId = getText
  }

/*  case class ScTopStat ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = "Top statement"
  }

  case class ScTopStatSeq ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = "Top statement sequence"
  }

  */

}