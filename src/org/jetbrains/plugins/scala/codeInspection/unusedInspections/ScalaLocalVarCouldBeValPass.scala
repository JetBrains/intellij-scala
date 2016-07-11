package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.{VarCouldBeValInspection, VarToValFix}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaLocalVarCouldBeValPass(file: ScalaFile, document: Document) extends ScalaInspectionBasedHighlightingPass(file, document) {
  override val inspectionShortName: String = VarCouldBeValInspection.ShortName

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case f: ScFunction if ScFunction.isSpecial(f.name) => false
    case m: ScMember if m.hasModifierProperty(ScalaKeyword.IMPLICIT) => false
    case _ => isEnabled(elem) && ScalaPsiUtil.isLocalOrPrivate(elem)
  }

  override def processElement(elem: PsiElement): Seq[Annotation] = elem match {
    case varDef: ScVariableDefinition =>
      var couldBeVal = true
      var used = false
      varDef.declaredElements.foreach { elem =>
        val holder = ScalaRefCountHolder.getInstance(file)
        holder.retrieveUnusedReferencesInfo { () =>
          if (holder.isValueWriteUsed(elem)) {
            couldBeVal = false
          }
          if (holder.isValueUsed(elem)) {
            used = true
          }
        }
      }
      if (couldBeVal && used) {
        val start = varDef.varKeyword.getTextRange.getStartOffset
        val end = varDef.getTextRange.getEndOffset
        val annotation = new Annotation(start, end, severity, VarCouldBeValInspection.Annotation, null)
        val fix = new VarToValFix(varDef)
        annotation.registerFix(fix, new TextRange(start, end), highlightKey)
        Seq(annotation)
      } else Seq.empty
    case _ => Seq.empty
  }
}
