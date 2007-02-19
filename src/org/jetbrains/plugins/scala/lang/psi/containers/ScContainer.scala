package org.jetbrains.plugins.scala.lang.psi.containers;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.search._

import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.resolve.processors._
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import org.jetbrains.plugins.scala.lang.psi._

/**
*  Trait that describes behavior of container which can include
*  import statements
*
*/
trait Importable extends ScalaPsiElement{

  /**
  *   Return all import expression in current container
  *
  */
  def getImportExprs = {
    val importStatements = childrenOfType[ScImportStmt](ScalaElementTypes.IMPORT_STMT_BIT_SET)
    (importStatements :\ (Nil: List[ScImportExpr]))((y: ScImportStmt, x: List[ScImportExpr]) => y.getImportExprs ::: x)
  }

  /**
  *   Returns full-qualified name of class if it is contained in current
  *   Importable instance or null othervise
  *
  */
  def getQualifiedName (shortName: String) : String = {
    for (val importExpr <- getImportExprs) {
      if (importExpr.isPlain && shortName.equals(importExpr.getTailId)) {
        return importExpr.getText
      }
    }
    null
  }

  def getClassByName(shortName: String, elem: PsiElement) : PsiElement = {
    val qualName = getQualifiedName(shortName)
    if (qualName != null) {
      val manager = PsiManager.getInstance(elem.getProject)
      return manager.findClass(qualName, elem.getResolveScope())
    }
    null
  }


}