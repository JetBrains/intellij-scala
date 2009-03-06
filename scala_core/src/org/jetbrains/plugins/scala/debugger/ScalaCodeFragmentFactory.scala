package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.evaluation.{CodeFragmentFactory, TextWithImports}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.{JavaCodeFragment, JavaPsiFacade, PsiElement}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr.ScReferenceExpression
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.typedef.ScObject
import lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.02.2009
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE


  def isContextAccepted(context: PsiElement): Boolean = context != null && context.getLanguage == ScalaFileType.SCALA_LANGUAGE

  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val text = item.getText
    val imports = item.getImports
    val toEval = ScalaPsiElementFactory.createScalaFile(text, context.getManager)
    val elementFactory = JavaPsiFacade.getInstance(toEval.getProject).getElementFactory
    def convertToJava(element: PsiElement): String = {
      val res = new StringBuilder("")
      element match {
        case _: ScalaFile => {
          for (child <- element.getNode.getChildren(null); psi = child.getPsi) res.append(convertToJava(psi))
        }
        case e if e.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR => res.append(";\n")
        case _ =>
      }
      res.toString
    }
    val javaText = convertToJava(toEval)
    val result = elementFactory.createCodeBlockCodeFragment(javaText.toString, null, true)
    result
  }

  def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val result = new ScalaCodeFragment(project, item.getText)
    result.setContext(context)
    result
  }
}