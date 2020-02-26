package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScReferencePattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createWildcardNode, createWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaMethodDescriptor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.project.ProjectContext

class DeleteUnusedElementFix(e: ScNamedElement) extends LocalQuickFixAndIntentionActionOnPsiElement(e) {
  override def getText: String = ScalaInspectionBundle.message("remove.unused.element")

  override def getFamilyName: String = getText

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (FileModificationService.getInstance.prepareFileForWrite(startElement.getContainingFile)) {
      implicit val ctx: ProjectContext = project

      def wildcard = createWildcardNode.getPsi

      startElement match {
        case ref: ScReferencePattern => ref.getContext match {
          case pList: ScPatternList if pList.patterns == Seq(ref) =>
            val context: PsiElement = pList.getContext
            context.getContext.deleteChildRange(context, context)
          case pList: ScPatternList if pList.simplePatterns && pList.patterns.startsWith(Seq(ref)) =>
            val end = ref.nextSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getNextSiblingNotWhitespace.getPrevSibling
            pList.deleteChildRange(ref, end)
          case pList: ScPatternList if pList.simplePatterns =>
            val start = ref.prevSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getPrevSiblingNotWhitespace.getNextSibling
            pList.deleteChildRange(start, ref)
          case _ =>
            // val (a, b) = t
            // val (_, b) = t
            ref.replace(createWildcardPattern)
        }
        case typed: ScTypedPattern => typed.nameId.replace(wildcard)
        case p: ScParameter =>
          if (p.owner.is[ScFunctionExpr]) p.nameId.replace(wildcard)
          else removeParameter(p)(project)
        case naming: ScNamingPattern => naming.replace(naming.named)
        case _ => startElement.delete()
      }
    }
  }

  private def removeParameter(p: ScParameter)(implicit project: Project): Unit = {
    val method = p.owner.asInstanceOf[ScMethodLike]
    def withoutParam(params: Seq[ScalaParameterInfo]) = params.filterNot(_.oldIndex == p.index)
    val filteredParameters = ScalaParameterInfo.allForMethod(method).map(withoutParam).filter(_.nonEmpty)
    // if no parameter/clause is left still leave an empty parameter clause
    val parameters = if (filteredParameters.isEmpty) Seq(Seq.empty) else filteredParameters
    val methodDesc = new ScalaMethodDescriptor(method)
    val retTy = method.asOptionOf[ScFunction].flatMap(_.returnType.toOption).getOrElse(Any)
    val changeInfo = ScalaChangeInfo(
      newVisibility = methodDesc.getVisibility,
      function = method,
      newName = method.name,
      newType = retTy,
      newParams = parameters,
      isAddDefaultArgs = false
    )
    val processor = new ScalaChangeSignatureProcessor(changeInfo)

    inWriteAction {
      processor.run()
    }
  }
}
