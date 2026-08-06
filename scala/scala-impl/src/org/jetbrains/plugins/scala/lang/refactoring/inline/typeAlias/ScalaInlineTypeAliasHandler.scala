package org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiReference
import com.intellij.refactoring.HelpID
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineActionHandler
import org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias.ScalaInlineTypeAliasHandler.{isParametrizedTypeAlias, isSimpleTypeAlias}

final class ScalaInlineTypeAliasHandler extends ScalaInlineActionHandler {
  override protected val helpId: String = ScalaInlineTypeAliasHandler.HelpId
  override protected val refactoringName: String = ScalaInlineTypeAliasHandler.RefactoringName

  override protected def canInlineScalaElement(element: ScalaPsiElement): Boolean = element.is[ScTypeAliasDefinition]

  override protected def checkNotUsedInStableRef(refs: Iterable[PsiReference])
                                                (implicit project: Project, editor: Editor): Boolean = true

  override def inlineScalaElement(element: ScalaPsiElement)(implicit project: Project, editor: Editor): Unit = element match {
    case typeAlias: ScTypeAliasDefinition =>
      if (isParametrizedTypeAlias(typeAlias) || !isSimpleTypeAlias(typeAlias)) {
        showErrorHint(ScalaBundle.message("cannot.inline.notsimple.typealias"))
      } else {
        if (validateReferences(typeAlias)) {
          val dialog = new ScalaInlineTypeAliasDialog(typeAlias)
          showDialog(dialog)
        }
      }
    case _ =>
  }
}

object ScalaInlineTypeAliasHandler {
  val HelpId: String = HelpID.INLINE_VARIABLE // TODO: more appropriate id?

  @NlsContexts.DialogTitle
  val RefactoringName: String = ScalaBundle.message("inline.type.alias.title")

  private def isSimpleTypeAlias(typeAlias: ScTypeAliasDefinition): Boolean =
    typeAlias.aliasedTypeElement.toSeq.flatMap(_.depthFirst()).forall {
      case t: ScTypeElement =>
        t.calcType match {
          case _: TypeParameterType => false
          case part: ScProjectionType if !ScalaPsiUtil.hasStablePath(part.element) =>
            false
          case _ => true
        }
      case _ => true
    }

  private def isParametrizedTypeAlias(typeAlias: ScTypeAliasDefinition) = typeAlias.typeParameters.nonEmpty
}
