package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesConfiguration, ScalaFindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum

import java.util
import java.util.Collections

class ScalaHighlightEnumUsagesHandler(target: ScalaPsiElement, file: PsiFile, editor: Editor)
    extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def getTargets: util.List[PsiElement] = target match {
    case enum: ScEnum =>
      enum.syntheticClass match {
        case Some(cls) => util.List.of(enum, cls)
        case None      => util.List.of(enum)
      }
    case enumCase: ScEnumCase => util.List.of(enumCase, enumCase.getSyntheticCounterpart)
    case _                    => Collections.emptyList()
  }

  override def selectTargets(
    targets:           util.List[_ <: PsiElement],
    selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]
  ): Unit =
    selectionConsumer.consume(targets)

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    val project = file.getProject
    val config = ScalaFindUsagesConfiguration.getInstance(project)
    val localSearchScope = new LocalSearchScope(file)

    targets.forEach { target =>
      val manager = new ScalaFindUsagesHandler(target, config)

      target match {
        case named: ScNamedElement if named.isPhysical => addOccurrence(named.nameId)
        case _                                         => ()
      }

      manager
        .findReferencesToHighlight(target, localSearchScope)
        .forEach {
          case scRef: ScReference => addOccurrence(scRef.nameId)
          case e => e.getElement
        }
    }
  }
}
