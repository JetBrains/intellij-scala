package org.jetbrains.plugins.scala
package codeInspection
package sbt

import com.intellij.codeInspection.ProblemsHolder
import lang.psi.api.ScalaFile
import lang.psi.api.expr.ScExpression
import lang.psi.types.result.{Failure, Success, TypingContext}
import lang.psi.impl.{ScalaPsiElementFactory, SbtFile}
import lang.lexer.{ScalaTokenTypes, ScalaElementType}
import com.intellij.psi.{PsiErrorElement, PsiWhiteSpace, PsiComment, PsiElement}
import lang.psi.api.toplevel.imports.ScImportStmt

class SbtFileStructureInspection extends AbstractInspection("ScalaSbtFileStructure", "SBT Light Configuration File Structure") {
  val description: String = "Checks that .sbt files contain only an optional import section followed by blank line delimited setting expressions."

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case f: ScalaFile if SbtFile.isSbtFile(f) =>
      def code = f.children.dropWhile {
        case _: PsiWhiteSpace | _: ScImportStmt | _: PsiComment => true
        case _ => false
      }.filterNot {
        case _: PsiComment | _: PsiWhiteSpace => true
        case _ => false
      }
      code.zip(code.drop(1)) foreach {
        case (c1, c2) =>
          var next = c1.getNextSibling
          var foundBlank = false
          while (next != c2) {
            if (next.isInstanceOf[PsiWhiteSpace] && next.getText.count(_ == '\n') > 1) {
              foundBlank = true
            }
            next = next.getNextSibling
          }
          if (!foundBlank && !c1.getNextSibling.isInstanceOf[PsiErrorElement]) {
            holder.registerProblem(c1.getNextSibling, "Blank line required between settings")
          }
      }
      val expectedSettingsType = ScalaPsiElementFactory.createTypeFromText("sbt.Project.SettingsDefinition", f, f.getLastChild)
      code foreach {
        case _: PsiErrorElement =>
        case ex: ScExpression =>
          val result = ex.getType(TypingContext.empty)
          result match {
            case Success(tpe, _) =>
              if (!tpe.conforms(expectedSettingsType)) {
                holder.registerProblem(ex, "Expected expression of type %s, found %s".format(expectedSettingsType.presentableText, tpe.presentableText))
              }
            case Failure(_, _) =>
          }
        case other =>
          if (other.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
            holder.registerProblem(other, "Semi-colon not allowed, use blank lines to separate settings.")
          } else {
            holder.registerProblem(other, "Expected expression")
          }
      }
  }
}