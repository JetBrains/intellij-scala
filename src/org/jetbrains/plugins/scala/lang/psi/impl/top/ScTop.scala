package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScTmplDef
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:13:02
 */

  class ScCompilationUnit ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString = "Compilation unit"
  }

  class ScPackaging ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with BlockedIndent{
    override def toString = "Packaging"

    def getTmplDefs : Iterable[ScTmplDef] = childrenOfType[ScTmplDef] (ScalaElementTypes.TMPL_DEF_BIT_SET)

    [NotNull]
    def getFullPackageName : String = getChild[ScQualId].getFullName
  }

  class ScPackageStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString = "Package statement"

    [Nullable]
    def getFullPackageName : String = {
      val qualId = getChild[ScQualId]
      if (qualId == null) null else qualId.getFullName
    }
  }

  case class ScQualId ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString = "Qualified identifier"

    //todo: change stableId parsing to qualId parsing
    def getFullName = getText
  }
}