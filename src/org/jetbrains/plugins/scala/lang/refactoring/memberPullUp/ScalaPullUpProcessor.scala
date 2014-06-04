package org.jetbrains.plugins.scala
package lang.refactoring.memberPullUp

import com.intellij.refactoring.{RefactoringBundle, BaseRefactoringProcessor}
import com.intellij.openapi.project.Project
import com.intellij.usageView.{UsageViewDescriptor, UsageInfo}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractMemberInfo
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, Success}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil

/**
 * Nikolay.Tropin
 * 2014-05-27
 */
class ScalaPullUpProcessor(project: Project,
                           sourceClass: ScTemplateDefinition,
                           targetClass: ScTemplateDefinition,
                           memberInfos: Seq[ScalaExtractMemberInfo]) extends BaseRefactoringProcessor(project) {
  override def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor =
    new PullUpUsageViewDescriptor

  override def getCommandName = RefactoringBundle.message("pullUp.command", sourceClass.name)

  override def performRefactoring(usages: Array[UsageInfo]) = {

  }

  override def findUsages() = Array[UsageInfo]()

  def moveMembersToBase() {
    val manager = targetClass.getManager
    val extendsBlock = targetClass.extendsBlock
    val templateBody = extendsBlock.templateBody match {
      case Some(tb) => tb
      case None => extendsBlock.add(ScalaPsiElementFactory.createTemplateBody(manager))
    }
    val anchor = templateBody.getLastChild

    val collectImportScope = memberInfos.collect {
      case ScalaExtractMemberInfo(m, false) => m
    } //extracted declarations are handled with ScalaPsiUtil.adjustTypes

    ScalaChangeContextUtil.encodeContextInfo(collectImportScope)

    extensions.inWriteCommandAction(project, "Pull up members") {
      extensions.withDisabledPostprocessFormatting(project) {
        val movedDefinitions = ArrayBuffer[ScMember]()
        for {
          info <- memberInfos
          memberCopy <- membersToExtract(info)
        } {
          handleOldMember(info)

          templateBody.addBefore(ScalaPsiElementFactory.createNewLine(manager), anchor)
          val added = templateBody.addBefore(memberCopy, anchor).asInstanceOf[ScMember]
          if (info.isToAbstract) ScalaPsiUtil.adjustTypes(added)
          else movedDefinitions += added
        }
        templateBody.addBefore(ScalaPsiElementFactory.createNewLine(manager), anchor)

        ScalaChangeContextUtil.decodeContextInfo(movedDefinitions)
      }

      for (tb <- sourceClass.extendsBlock.templateBody if tb.members.isEmpty) {
        tb.delete()
      }

      reformatAfter()
    }
  }

  private def reformatAfter() {
    val documentManager = PsiDocumentManager.getInstance(project)
    val csManager = CodeStyleManager.getInstance(project)
    val targetDocument = documentManager.getDocument(targetClass.getContainingFile)
    documentManager.doPostponedOperationsAndUnblockDocument(targetDocument)
    csManager.reformat(targetClass)
    val sourceDocument = documentManager.getDocument(sourceClass.getContainingFile)
    documentManager.doPostponedOperationsAndUnblockDocument(sourceDocument)
    csManager.adjustLineIndent(sourceClass.getContainingFile, sourceClass.getTextRange)
  }

  private def membersToExtract(info: ScalaExtractMemberInfo): Seq[ScMember] = {
    info match {
      case ScalaExtractMemberInfo(decl: ScDeclaration, _) =>
        val member = decl.copy().asInstanceOf[ScMember]
        Seq(member)
      case ScalaExtractMemberInfo(m, true) =>
        declarationsText(m).map(ScalaPsiElementFactory.createDeclarationFromText(_, m.getParent, m).asInstanceOf[ScMember])
      case ScalaExtractMemberInfo(m, false) =>
        val copy = m.copy().asInstanceOf[ScMember]
        copy.setModifierProperty("override", value = false)
        val shift = "override ".length
        ScalaChangeContextUtil.shiftAssociations(copy, - shift)
        Seq(copy)
    }
  }

  private def handleOldMember(info: ScalaExtractMemberInfo) = {
    info match {
      case ScalaExtractMemberInfo(m: ScDeclaration, _) => m.delete()
      case ScalaExtractMemberInfo(m, false) => m.delete()
      case ScalaExtractMemberInfo(m, true) => m.setModifierProperty("override", value = true)
    }
  }

  private def declarationsText(m: ScMember): Seq[String] = {
    def textForBinding(b: ScBindingPattern) = {
      val typeText = b.getType(TypingContext.empty) match {
        case Success(t, _) => s": ${t.canonicalText}"
        case _ => ""
      }
      s"${b.name}$typeText"
    }
    m match {
      case decl: ScDeclaration => Seq(decl.getText)
      case funDef: ScFunctionDefinition =>
        val copy = funDef.copy().asInstanceOf[ScFunctionDefinition]
        copy.setModifierProperty("override", value = false)
        Seq(copy.assignment, copy.body).flatten.foreach(_.delete())
        copy.accept(new ScalaRecursiveElementVisitor() {
          override def visitSimpleTypeElement(te: ScSimpleTypeElement) = {
            val tpe = te.calcType
            te.replace(ScalaPsiElementFactory.createTypeElementFromText(tpe.canonicalText, te.getManager))
          }
        })
        Seq(copy.getText)
      case valDef: ScPatternDefinition =>
        val copy = valDef.copy().asInstanceOf[ScPatternDefinition]
        copy.bindings.collect {
          case b: ScBindingPattern => "val " + textForBinding(b)
        }
      case varDef: ScVariableDefinition =>
        val copy = varDef.copy().asInstanceOf[ScVariableDefinition]
        copy.bindings.collect {
          case b: ScBindingPattern => "var " + textForBinding(b)
        }
      case ta: ScTypeAliasDefinition =>
        val copy = ta.copy().asInstanceOf[ScTypeAliasDefinition]
        Seq(
          Option(copy.findFirstChildByType(ScalaTokenTypes.tASSIGN)),
          Option(copy.findFirstChildByType(ScalaTokenTypes.tUPPER_BOUND)),
          Option(copy.findFirstChildByType(ScalaTokenTypes.tLOWER_BOUND)),
          Option(copy.aliasedTypeElement)
        ).flatten.foreach(_.delete())
        Seq(copy.getText)
      case _ => throw new IllegalArgumentException(s"Cannot create declaration text from member ${m.getText}")
    }
  }
  
  private class PullUpUsageViewDescriptor extends UsageViewDescriptor {
    def getProcessedElementsHeader: String = "Pull up members from"

    def getElements: Array[PsiElement] = Array[PsiElement](sourceClass)

    def getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
      s"Class to pull up members to ${targetClass.name}"

    def getCommentReferencesText(usagesCount: Int, filesCount: Int): String = null
  }
}
