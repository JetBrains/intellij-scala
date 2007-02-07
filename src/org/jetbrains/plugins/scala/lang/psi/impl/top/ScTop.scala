package org.jetbrains.plugins.scala.lang.psi.impl.top

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScTmplDef
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

/**********************************************************************************************************************/
/******************************************** Main top definitions ****************************************************/
/**********************************************************************************************************************/

class ScCompilationUnit(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString = "Compilation unit"
}

/**
* Implements behaviour of package body
*
*/
class ScPackaging(node: ASTNode) extends ScalaPsiElementImpl (node) with BlockedIndent{
  override def toString = "Packaging"

  /**
  *
  * @retruns Template definitions inside current packaging
  */
  def getTmplDefs: Iterable[ScTmplDef] = {
    val children = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTmplDef]))((y: ScalaPsiElementImpl, x: List[ScTmplDef]) =>
      y.getNode.getElementType match
      {
        case ScalaElementTypes.PACKAGING => y.asInstanceOf[ScPackaging].getTmplDefs.toList ::: x
        case _ => y.asInstanceOf[ScTmplDef].getTmplDefs.toList ::: x
      }
    )

  }

  [NotNull]
  def getFullPackageName: String = getChild(ScalaElementTypes.QUAL_ID).asInstanceOf[ScQualId].getFullName

}



class ScPackageStatement(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString = "Package statement"

  [Nullable]
  def getFullPackageName: String = {
    val qualId = getChild(ScalaElementTypes.QUAL_ID).asInstanceOf[ScQualId]
    if (qualId == null) null else qualId.getFullName
  }
}



case class ScQualId(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString = "Qualified identifier"

  //todo: change stableId parsing to qualId parsing
  def getFullName = getText
}
