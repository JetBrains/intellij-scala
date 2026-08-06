package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.CompletionCommandKt.getCommandContext
import com.intellij.codeInsight.completion.command.commands.AbstractRenameActionCommandProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTemplateDefinition}

//noinspection UnstableApiUsage
final class ScalaRenameActionCommandProvider extends AbstractRenameActionCommandProvider {
  @Nullable
  override def findRenameOffset(offset: Int, psiFile: PsiFile): Integer =
    getVisibleCommandContextWithOffset(offset, psiFile) match {
      case Some((element, offset)) =>
        val finalOffset: Int = getMethodRenameOffset(element, offset)
          .orElse(getClassRenameOffset(element, offset))
          .getOrElse(offset)
        finalOffset
      case _ => null
    }

  private def getVisibleCommandContextWithOffset(offset: Int, psiFile: PsiFile): Option[(PsiElement, Int)] =
    if (offset < 1) None
    else getCommandContext(offset, psiFile).toOption.flatMap {
      case ws: PsiWhiteSpace =>
        PsiTreeUtil.prevVisibleLeaf(ws).toOption.map(leaf => (leaf, leaf.endOffset))
      case element => Some((element, offset))
    }

  //def something..[T]..(a: T)..: T = {
  //}..
  //def something..[T]..(a: T)..=
  //`..` -- place to call 'rename'
  private def getMethodRenameOffset(element: PsiElement, currentOffset: Int): Option[Int] =
    element.parentOfType[ScFunction].flatMap { function =>
      function.getIdentifyingElement.toOption.flatMap { id =>
        val offset = id.endOffset
        if (
          offset == currentOffset ||
            typeParametersClauseEndsAt(function, currentOffset) ||
            parametersClauseEndsAt(function, currentOffset) ||
            bodyBlockEndsAt(function, currentOffset)
        ) Some(offset)
        else None
      }
    }

  //class Something..[T]..(val a: T).. {}..
  //`..` -- place to call 'rename'
  private def getClassRenameOffset(element: PsiElement, currentOffset: Int): Option[Int] = {
    element.parentOfType[ScTemplateDefinition].flatMap { td =>
      td.getIdentifyingElement.toOption.flatMap { id =>
        val offset = id.endOffset
        if (
          offset == currentOffset ||
            typeParametersClauseEndsAt(td, currentOffset) ||
            td.asOptionOf[ScConstructorOwner].flatMap(_.constructor).exists(parametersClauseEndsAt(_, currentOffset)) ||
            td.extendsBlock.templateBody.exists(rightBraceEndsAt(_, currentOffset))
        ) Some(offset)
        else None
      }
    }
  }

  private def parametersClauseEndsAt(e: ScParameterOwner, offset: Int): Boolean = e.clauses match {
    case Some(clauses) =>
      clauses.endOffset == offset ||
        clauses.clauses.exists(_.endOffset == offset)
    case _ => false
  }

  private def typeParametersClauseEndsAt(e: PsiElement, offset: Int): Boolean = e match {
    case owner: ScTypeParametersOwner => owner.typeParameterClauses.exists(_.endOffset == offset)
    case _ => false
  }

  private def bodyBlockEndsAt(f: ScFunction, offset: Int): Boolean = f match {
    case funDef: ScFunctionDefinition =>
      val body = funDef.body
      val block = body.filterByType[ScOptionalBracesOwner]
      block.exists(rightBraceEndsAt(_, offset))
    case _ => false
  }

  private def rightBraceEndsAt(e: ScOptionalBracesOwner, offset: Int): Boolean =
    e.getRBrace.exists(_.endOffset == offset)
}
