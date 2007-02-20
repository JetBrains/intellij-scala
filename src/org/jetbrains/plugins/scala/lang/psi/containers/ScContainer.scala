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
  private def getImportExprs = {
    val importStatements = childrenOfType[ScImportStmt](ScalaElementTypes.IMPORT_STMT_BIT_SET)
    (importStatements :\ (Nil: List[ScImportExpr]))((y: ScImportStmt, x: List[ScImportExpr]) => y.getImportExprs ::: x)
  }

  /**
  *   Returns full-qualified name of class if it is contained in current
  *   Importable instance or null othervise
  *
  */
  private def getQualifiedName(shortName: String): String = {
    for (val importExpr <- getImportExprs) {
      val qualName = importExpr.getExplicitName(shortName)
      if (qualName != null){
        return qualName
      }
    }
    null
  }

  /**
  *   Returns class element by qualified name if it exists in current scope
  *   or null otherwise
  *
  */
  private def getClassByName(shortName: String): PsiElement = {
    val qualName = getQualifiedName(shortName)
    if (qualName != null) {
      val manager = PsiManager.getInstance(this.getProject)
      return manager.findClass(qualName, this.getResolveScope())
    }
    null
  }

  /**
  *   Searches for given name belong wildcard imports
  *
  */
  private def combWildcards(shortName: String) : PsiElement = {
    val manager = PsiManager.getInstance(this.getProject)
    for (val importExpr <- getImportExprs) {
      if (importExpr.hasWildcard) {
        val qualName = importExpr.getImportReference.getText + "." + shortName
        val result = manager.findClass(qualName, this.getResolveScope())
        if (result != null) return result
      }
    }
    null
  }


  /**
  *   Retruns class according to current processor and substitutor
  *
  */
  def getClazz(getDeclarations: => Iterable[PsiElement], processor: PsiScopeProcessor, substitutor: PsiSubstitutor): Boolean =
  {
    /*
        1. May be it is among local definitions  
    */
    for (val tmplDef <- getDeclarations) {
      if (! processor.execute(tmplDef, substitutor)) {
        return false
      }
    }

    /*
        2. May be it is among explicit imports?
    */
    var clazz = getClassByName(processor.asInstanceOf[ScalaPsiScopeProcessor].getName)
    if (clazz != null) {
      processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
      return false
    }

    /*
       3. May be it is among wildcard imports?
    */
    clazz = combWildcards(processor.asInstanceOf[ScalaPsiScopeProcessor].getName)
    if (clazz != null) {
      processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
      return false
    }

    return true
  }


}