package org.jetbrains.plugins.scala.lang.psi.impl.top.defs

/** 
* @author ilyas
*/

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem

import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._


/*
*   Trait or class definition implementation
*/
class ScTypeDefinition(node: ASTNode) extends ScTmplDef(node)  with IfElseIndent{
  import _root_.scala.tools.nsc.util._

  override def toString: String = "type" + " " + super.toString

  def getTemplateBody = getChild(ScalaElementTypes.TEMPLATE_BODY).asInstanceOf[ScTemplateBody]

  def getTemplateParents = getChild(ScalaElementTypes.TEMPLATE_PARENTS).asInstanceOf[ScTemplateParents]

  def getMainParentName = {
    if (getTemplateParents != null &&
    getTemplateParents.getMainConstructor != null){
      getTemplateParents.getMainConstructor.getClassName
    } else {
      null
    }
  }

  def getMixinParentsNames = {
    if (getTemplateParents != null){
      getTemplateParents.getMixinParents.toList
    } else {
      Nil: List[ScStableId]
    }
  }

  /**
  *  Retruns names of immediate parents
  */
  def getImmediateParentsNames = if (getMainParentName != null) {
    getMainParentName :: getMixinParentsNames
  }  else {
    getMixinParentsNames
  }

  def getAllParents(alreadyHas: List[ScTypeDefinition]): List[ScTypeDefinition] = {

    def getImmediateParents(list: List[ScTypeDefinition]) =
      getImmediateParentsNames.map[ScTypeDefinition]((s: ScStableId) =>
        s.getReference.resolve.asInstanceOf[ScTypeDefinition]).filter((e: ScTypeDefinition) => ! list.contains(e))

    var newParents = Nil: List[ScTypeDefinition]
    if (alreadyHas != null && getImmediateParents(alreadyHas) != null){
      for (val parent <- getImmediateParents(alreadyHas)){
        if (parent != null && newParents != null &&
        parent.getAllParents(parent :: alreadyHas ::: newParents) != null) {
          newParents = newParents ::: parent :: parent.getAllParents(parent :: alreadyHas ::: newParents)
        }
      }
    }
    newParents
  }

  /**
  *  Retruns names of all immediate parents
  */
  def getAllParents: List[ScTypeDefinition] = {
    getAllParents(Nil: List[ScTypeDefinition])
  }

  /**
  *  Returns own template statements of current type definition
  */
  def getOwnTemplateStatements: List[ScTemplateStatement] = {
    //    var statementSet = new HashSet[ScTemplateStatement](239)
    var statList = Nil: List[ScTemplateStatement]

    if (getTemplateBody != null &&
    getTemplateBody.asInstanceOf[ScTemplateBody].getTemplateStatements != null){
      for (val statement <- getTemplateBody.asInstanceOf[ScTemplateBody].getTemplateStatements) {
        //        statementSet.addEntry(statement.asInstanceOf[ScTemplateStatement])
        statList = statement.asInstanceOf[ScTemplateStatement] :: statList
      }
    }
    //statementSet.toList
    statList
  }

  /**
  *  Returns ALL template statements of current type definition (including inherited)
  */
  def getAllTemplateStatements: List[ScTemplateStatement] = {
    /*
        var statementSet = new HashSet[ScTemplateStatement](239)
        val reversedParentList = getAllParents.reverse

        Console.println("parents: " + reversedParentList.length)

        for (val parent <- reversedParentList){

          Console.println(parent + " " + parent.asInstanceOf[ScTmplDef].getQualifiedName + " " + (parent.asInstanceOf[ScTmplDef].getTemplateStatements != null))

          if (parent != null && parent.isInstanceOf[ScTmplDef] &&
          parent.asInstanceOf[ScTmplDef].getTemplateStatements != null) {

            Console.println(parent.asInstanceOf[ScTmplDef].getQualifiedName + " " +
            parent.asInstanceOf[ScTmplDef].getTemplateStatements.toList.length)

            for (val statement <- parent.asInstanceOf[ScTmplDef].getTemplateStatements) {
              statementSet.addEntry(statement.asInstanceOf[ScTemplateStatement])
            }
          }
        }
    */

    getOwnTemplateStatements // ::: statementSet.toList
  }

  def getAllMethods = getAllTemplateStatements.filter((stmt: ScTemplateStatement) => stmt.isInstanceOf[ScFunction])

  def getTypeParameterClause: ScTypeParamClause = {
    getChild(ScalaElementTypes.TYPE_PARAM_CLAUSE).asInstanceOf[ScTypeParamClause]
  }

}



