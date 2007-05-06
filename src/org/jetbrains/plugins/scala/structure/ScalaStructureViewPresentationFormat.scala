package org.jetbrains.plugins.scala.structure {

  import com.intellij.navigation.NavigationItem
  import com.intellij.ide.structureView.StructureViewTreeElement;
  import com.intellij.navigation.ItemPresentation;
  import com.intellij.navigation.NavigationItem;
  import com.intellij.openapi.editor.colors.TextAttributesKey;
  import com.intellij.openapi.util.Iconable;
  import com.intellij.ui.LayeredIcon;
  import com.intellij.ui.RowIcon;
  import com.intellij.ide.util.treeView.smartTree.TreeElement
  import com.intellij.psi._

  import javax.swing._;
  import java.util.ArrayList;
  import java.util.List;

  import org.jetbrains.plugins.scala.lang.psi.ScalaFile
  import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
  import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
  import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
  import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
  import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
  import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

  /**
  * @author: Dmitry.Krasilschikov
  * Date: 02.02.2007
  */

  object ScalaStructureViewPresentationFormat {

    def presentationText(element: ScalaPsiElement): String = {
      val name = element match {
        case file: ScalaFile => file.getVirtualFile.getName
        case packaging: ScPackaging => packaging.getFullPackageName
        case topDef: ScTmplDef => topDef.getName
        case templateStmt: ScTemplateStatement => templateStmt.getShortName
      }

      presentationText(name, element)
    }

    def presentationText(name: String, element: ScalaPsiElement): String = {
      element match {
        case templateStmt: ScTemplateStatement => presentationText(name, presentationOfStatement(templateStmt))
        case _ => name
      }
    }

    def presentationText(strings: String *): String = {
      var result: String = ""
      for (val string <- strings; string != null) yield result = result + string
      result
    }

    def presentationOfStatement(templateStmt: ScTemplateStatement): String = {
      val typeParamClauseString = templateStmt match {
        case function: ScFunction => if (function.getTypeParamClause != null) function.getTypeParamClause.getText else ""
        case typeDefinition: ScTypeAliasDefinition => if (typeDefinition.getTypeParamClause != null) typeDefinition.getTypeParamClause.getText else ""
        case _ => ""
      }

      val paramTypesString = templateStmt match {
        case function: ScFunction => {
          val allParamClauses = function.paramClauses

          def paramTypeAsString(param: ScParam) =
            if (param.paramType != null) param.paramType().getText
            else ""

          def paramClauseAsString(paramClause: ScParamClause): String = {
            if (paramClause != null)
              if (paramClause.params != null)
                paramClause.params.map[String](paramTypeAsString).mkString("(", ",", ")")
              else ""
            else ""
          }

          if (allParamClauses != null) {
            val map = allParamClauses.map[String](paramClauseAsString)
            map.mkString("", "", "")
          } else ""
        }

        case _ => ""
      }

      //        val exprPresentage = templateStmt match {
      //          case functionDef : ScFunctionDefinition => ""
      //          case definition : ScDefinition => definition.getExpr.getText
      //          case _ => null
      //        }

      val stmtType = templateStmt.getType

      if (stmtType != null) presentationText(typeParamClauseString, paramTypesString, ":", stmtType.getText)
      else presentationText(typeParamClauseString, paramTypesString)
    }
  }
}