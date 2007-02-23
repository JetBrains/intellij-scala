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
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.javaView._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

/**
*  Trait that describes behavior of container which can include
*  import statements
*
*/
trait Importable extends ScalaPsiElement{

  var canBeObject = false

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
  private def getQualifiedName(shortName: String, prefix: String): String = {
    for (val importExpr <- getImportExprs) {
      val qualName = importExpr.getExplicitName(shortName, prefix)
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
  private def getInExplicitImports(shortName: String, prefix: String): PsiElement = {
    val qualName = getQualifiedName(shortName, prefix)
    if (qualName != null) {
      val manager = PsiManager.getInstance(this.getProject)
      val classes = manager.findClasses(qualName, this.getResolveScope())
      if (classes != null) {
        for (val clazz <- classes) {
          if (isValid(clazz, canBeObject)) {
            return clazz
          }
        }
        return null
      } else null

    }
    null
  }

  /**
  *   Process _root_ and nested imports
  *
  */
  private def stickNames(myRefText: String, prefix: String): String = {
    var refText = myRefText
    if (refText.length > 7 && refText.substring(0, 7).equals("_root_.")) {
      refText = refText.substring(7)
    } else {
      if (refText.contains(".")) {
        val importBegin = refText.substring(0, refText.indexOf("."))
        val index = prefix.indexOf(importBegin)
        if (index > 0 &&
        prefix.charAt(index - 1) == '.' &&
        prefix.charAt(index + importBegin.length) == '.'){
          refText = prefix.substring(0, index) + refText
        }
      }
    }
    refText
  }

  /**
  *   Searches for given name belong wildcard imports
  *
  */
  private def combWildcards(shortName: String, prefix: String): PsiElement = {
    val manager = PsiManager.getInstance(this.getProject)
    for (val importExpr <- getImportExprs) {
      if (importExpr.hasWildcard) {
        val qualName = stickNames(importExpr.getImportReference.getText, prefix) + "." + shortName
        val classes = manager.findClasses(qualName, this.getResolveScope())
        if (classes != null) {
          for (val clazz <- classes) {
            if (isValid(clazz, canBeObject)) {
              return clazz
            }
          }
        }
      }
    }
    null
  }

  /**
  *   searches for element in current scope (class, package etc)
  *
  */
  private def getInPackage(shortName: String): PsiElement = {
    val qualPrefix = ScalaResolveUtil.getQualifiedPrefix(this)
    val manager = PsiManager.getInstance(this.getProject)
    if (qualPrefix != null) {
      val classes = manager.findClasses(qualPrefix + shortName, this.getResolveScope())
      if (classes != null) {
        for (val clazz <- classes) {
          if (isValid(clazz, canBeObject)) {
            return clazz
          }
        }
        return null
      } else null
    } else null
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
         2. May be it is in current package?
    */
    var clazz = getInPackage(processor.asInstanceOf[ScalaPsiScopeProcessor].getName)
    if (clazz != null) {
      processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
      return false
    }

    /*
        3. May be it is among explicit imports?
    */
    clazz = getInExplicitImports(processor.asInstanceOf[ScalaPsiScopeProcessor].getName,
            ScalaResolveUtil.getQualifiedPrefix(this))
    if (clazz != null) {
      processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
      return false
    }

    /*
       4. May be it is among wildcard imports?
    */
    clazz = combWildcards(processor.asInstanceOf[ScalaPsiScopeProcessor].getName,
            ScalaResolveUtil.getQualifiedPrefix(this))
    if (clazz != null) {
      processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
      return false
    }

    /* We are already on top */
    if (this.isInstanceOf[PsiFile]) {
      val manager = PsiManager.getInstance(this.getProject)
      /*
         5. May be, it is in scala._ ?
      */
      val classes = manager.findClasses("scala." + processor.asInstanceOf[ScalaPsiScopeProcessor].getName, this.getResolveScope())
      if (classes != null) {
        for (val clazz <- classes) {
          if (isValid(clazz, canBeObject)) {
            processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
            return false
          }
        }
      }

      /*
         6. May be, it is in java.lang.*?
      */
      clazz = manager.findClass("java.lang." + processor.asInstanceOf[ScalaPsiScopeProcessor].getName)
      if (clazz != null) {
        processor.asInstanceOf[ScalaPsiScopeProcessor].setResult(clazz)
        return false
      }
    }

    return true
  }

  def isValid(clazz: PsiElement, canBeObject: Boolean) = {
    clazz.isInstanceOf[PsiClass] && (! clazz.isInstanceOf[ScJavaClass] ||
    (clazz.isInstanceOf[ScJavaClass] &&
    (! clazz.asInstanceOf[ScJavaClass].getClassInstance.isInstanceOf[ScObjectDefinition]) ||
    canBeObject))
  }


}