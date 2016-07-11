package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaUnusedLocalSymbolPass(file: ScalaFile, document: Document) extends ScalaInspectionBasedHighlightingPass(file, document) {
  override val inspectionShortName: String = ScalaUnusedSymbolInspection.ShortName

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case f: ScFunction if ScFunction.isSpecial(f.name) => false
    case m: ScMember if m.hasModifierProperty(ScalaKeyword.IMPLICIT) => false
    case _ => isEnabled(elem) && ScalaPsiUtil.isLocalOrPrivate(elem)
  }

  override def processElement(elem: PsiElement): Seq[Annotation] = elem match {
    case declaredHolder: ScDeclaredElementsHolder => declaredHolder.declaredElements.flatMap {
      case named: ScNamedElement =>
        val holder = ScalaRefCountHolder.getInstance(file)
        var used = false
        holder.retrieveUnusedReferencesInfo { () =>
          if (holder.isValueUsed(named)) {
            used = true
          }
        }
        if (!used) {
          val message = ScalaUnusedSymbolInspection.Annotation
          val range = named.nameId.getTextRange
          val annotation = new Annotation(range.getStartOffset, range.getEndOffset, severity, message, null)
          annotation.registerFix(new DeleteUnusedElementFix(named), range, highlightKey)
          Seq(annotation)
        } else Seq.empty
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }
}
