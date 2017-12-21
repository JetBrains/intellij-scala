package org.jetbrains.plugins.scala.findUsages.factory

import javax.swing.SwingUtilities

import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.refactoring.rename.RenameSuperMembersUtil
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory {

  val typeDefinitionOptions = new ScalaTypeDefinitionFindUsagesOptions(project)

  val memberOptions = new ScalaMemberFindUsagesOptions(project)

  val localOptions = new ScalaLocalFindUsagesOptions(project)

  override def canFindUsages(element: PsiElement): Boolean = {
    element match {
      case _: FakePsiMethod => true
      case _: ScTypedDefinition => true
      case _: ScTypeDefinition => true
      case _: ScPrimaryConstructor => true
      case _: ScTypeParam => true
      case _: PsiClassWrapper => true
      case _: PsiMethodWrapper => true
      case _ => false
    }
  }

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    var replacedElement = element match {
      case isWrapper(named) => named
      case FakePsiMethod(method) => method
      case _ => element
    }

    def chooseSuper(name: String, supers: Seq[PsiNamedElement]) {
      def showDialog() {
        val message = ScalaBundle.message("find.usages.member.has.supers", name)
        val result = Messages.showYesNoCancelDialog(element.getProject, message, "Warning", Messages.getQuestionIcon)
        result match {
          case 0 =>
            val elem = supers.last
            replacedElement = elem
          case 1 => //do nothing, it's ok
          case _ => replacedElement = null
        }
      }
      if (SwingUtilities.isEventDispatchThread) showDialog()
      else extensions.invokeAndWait(showDialog())
    }

    replacedElement match {
      case function: ScFunction if function.isLocal => Array(function)
      case named: ScNamedElement if !forHighlightUsages =>
        val supers = RenameSuperMembersUtil.allSuperMembers(named, withSelfType = true).filter(needToAsk)
        if (supers.nonEmpty) chooseSuper(named.name, supers)
      case _ =>
    }
    if (replacedElement == null) return FindUsagesHandler.NULL_HANDLER
    new ScalaFindUsagesHandler(replacedElement, this)
  }

  private def needToAsk(named: PsiNamedElement): Boolean = {
    named match {
      case fun: ScFunction
        if fun.containingClass.qualifiedName.startsWith("scala.Function") && fun.name == "apply" => false
      case _ => true
    }
  }
}

object ScalaFindUsagesHandlerFactory {
  def getInstance(project: Project): ScalaFindUsagesHandlerFactory = {
    ContainerUtil.findInstance(Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, project), classOf[ScalaFindUsagesHandlerFactory])
  }
}