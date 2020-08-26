package org.jetbrains.plugins.scala
package lang
package refactoring
package memberPullUp

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.refactoring.{BaseRefactoringProcessor, RefactoringBundle}
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractMemberInfo
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
  * Nikolay.Tropin
  * 2014-05-27
  */
final class ScalaPullUpProcessor(project: Project,
                                 sourceClass: ScTemplateDefinition,
                                 targetClass: ScTemplateDefinition,
                                 memberInfos: collection.Seq[ScalaExtractMemberInfo]) extends BaseRefactoringProcessor(project) {
  override def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor =
    new PullUpUsageViewDescriptor

  override def getCommandName: String = RefactoringBundle.message("pullUp.command", sourceClass.name)

  override def performRefactoring(usages: Array[UsageInfo]): Unit = {
  }

  override def findUsages(): Array[UsageInfo] = UsageInfo.EMPTY_ARRAY

  /**
    * Should be invoked in write action
    **/
  def moveMembersToBase(): Unit = {
    import ScalaChangeContextUtil._

    implicit val projectContext: ProjectContext = targetClass.projectContext
    val extendsBlock = targetClass.extendsBlock
    val templateBody = extendsBlock.templateBody match {
      case Some(tb) => tb
      case None => extendsBlock.add(createTemplateBody)
    }
    val anchor = templateBody.getLastChild

    memberInfos.collect {
      case ScalaExtractMemberInfo(m, false) => m // extracted declarations are handled with ScalaPsiUtil.adjustTypes
    }.foreach(encodeContextInfo)

    withDisabledPostprocessFormatting(project) {
      val movedDefinitions = mutable.ArrayBuffer.empty[ScMember]
      for {
        info <- memberInfos
        memberCopy <- memberCopiesToExtract(info)
      } {
        handleOldMember(info)

        templateBody.addBefore(createNewLine(), anchor)
        val added = templateBody.addBefore(memberCopy, anchor).asInstanceOf[ScMember]
        if (info.isToAbstract) TypeAdjuster.markToAdjust(added)
        else movedDefinitions += added
      }
      templateBody.addBefore(createNewLine(), anchor)

      for {
        definition <- movedDefinitions

        movedElement = movedMember(definition) match {
          case null => definition
          case moved => moved
        }
      } Associations.restoreFor(movedElement)
    }

    for (tb <- sourceClass.extendsBlock.templateBody if tb.members.isEmpty) {
      tb.delete()
    }

    reformatAfter()
  }

  private def reformatAfter(): Unit = {
    val documentManager = PsiDocumentManager.getInstance(project)
    val csManager = CodeStyleManager.getInstance(project)
    val targetDocument = documentManager.getDocument(targetClass.getContainingFile)
    documentManager.doPostponedOperationsAndUnblockDocument(targetDocument)
    csManager.reformat(targetClass)
    val sourceDocument = documentManager.getDocument(sourceClass.getContainingFile)
    documentManager.doPostponedOperationsAndUnblockDocument(sourceDocument)
    csManager.adjustLineIndent(sourceClass.getContainingFile, sourceClass.getTextRange)
  }

  private def memberCopiesToExtract(info: ScalaExtractMemberInfo): collection.Seq[ScMember] = {
    info match {
      case ScalaExtractMemberInfo(decl: ScDeclaration, _) =>
        val member = decl.copy().asInstanceOf[ScMember]
        Seq(member)
      case ScalaExtractMemberInfo(m, true) =>
        declarationsText(m).map(createDeclarationFromText(_, m.getParent, m).asInstanceOf[ScMember])
      case ScalaExtractMemberInfo(m, false) if m.hasModifierProperty("override") =>
        val copy = m.copy().asInstanceOf[ScMember]
        copy.setModifierProperty("override", value = false)
        val shift = "override ".length
        Associations.shiftFor(copy, -shift)
        Seq(copy)
      case _ => Seq(info.getMember.copy().asInstanceOf[ScMember])
    }
  }

  private def handleOldMember(info: ScalaExtractMemberInfo): Unit = info.getMember match {
    case member if !member.isInstanceOf[ScDeclaration] && info.isToAbstract =>
      member.setModifierProperty("override")
    case member => member.delete()
  }

  private def declarationsText(m: ScMember): collection.Seq[String] = {
    def textForBinding(b: ScBindingPattern) = {
      val typeText = b.`type`() match {
        case Right(t) => s": ${t.canonicalCodeText}"
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
          override def visitSimpleTypeElement(te: ScSimpleTypeElement): Unit = {
            val tpe = te.calcType
            te.replace(createTypeElementFromText(tpe.canonicalCodeText)(te.getManager))
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
          copy.aliasedTypeElement
        ).flatten.foreach(_.delete())
        Seq(copy.getText)
      case _ => throw new IllegalArgumentException(s"Cannot create declaration text from member ${m.getText}")
    }
  }

  private class PullUpUsageViewDescriptor extends UsageViewDescriptor {
    override def getProcessedElementsHeader: String = ScalaBundle.message("pull.up.members.from")

    override def getElements: Array[PsiElement] = Array[PsiElement](sourceClass)

    override def getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
      ScalaBundle.message("class.to.pull.up.members.to.class", targetClass.name)

    override def getCommentReferencesText(usagesCount: Int, filesCount: Int): String = null
  }

}
