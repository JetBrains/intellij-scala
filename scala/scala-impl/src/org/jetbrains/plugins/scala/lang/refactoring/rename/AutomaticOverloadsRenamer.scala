package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.{AutomaticRenamer, AutomaticRenamerFactory}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ExtractDesignated
import org.jetbrains.plugins.scala.lang.refactoring.rename.AutomaticOverloadsRenamer.getOverloads
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.util

final class AutomaticOverloadsRenamer(function: ScFunction, newName: String) extends AutomaticRenamer {
  locally {
    val overloads = getOverloads(function)
    overloads.withFilter(_ != function).foreach { overload =>
      myElements.add(overload)
    }
    suggestAllNames(function.name, newName)
  }

  override def getDialogTitle: String = JavaRefactoringBundle.message("rename.overloads.dialog.title");

  override def getDialogDescription: String = JavaRefactoringBundle.message("rename.overloads.to.dialog.description")

  override def entityName(): String = JavaRefactoringBundle.message("automatic.overload.renamer.entity.name")

  override def isSelectedByDefault: Boolean = true
}

final class AutomaticOverloadsRenamerFactory extends AutomaticRenamerFactory {
  override def isApplicable(element: PsiElement): Boolean = element match {
    case fn: ScFunction => !fn.isConstructor && !fn.isLocal
    case _ => false
  }

  override def createRenamer(element: PsiElement, newName: String, usages: util.Collection[UsageInfo]): AutomaticRenamer =
    new AutomaticOverloadsRenamer(element.asInstanceOf[ScFunction], newName)

  override def isEnabled: Boolean = ScalaApplicationSettings.getInstance().RENAME_OVERLOADS

  override def setEnabled(enabled: Boolean): Unit = ScalaApplicationSettings.getInstance().RENAME_OVERLOADS = enabled

  override def getOptionName: String = JavaRefactoringBundle.message("rename.overloads")
}

private object AutomaticOverloadsRenamer {
  private def getFunctions(member: ScMember): Seq[ScFunction] = member match {
    case fn: ScFunction => Seq(fn)
    case ext: ScExtension => ext.extensionMethods
    case _ => Seq.empty
  }

  def getOverloads(function: ScFunction): Seq[ScFunction] = {
    val containingClass = function.containingClass
    val classFunctions = containingClass match {
      case null => Seq.empty
      case cls => cls.members.flatMap(getFunctions)
    }

    val packageFunctions = for {
      pkg <- function.parentsInFile.filterByType[ScPackageLike].nextOption().toSeq
      psiManager = ScalaPsiManager.instance(function.getProject)
      function <- psiManager.getTopLevelDefinitionsByPackage(pkg.fqn, function.resolveScope).filterByType[ScFunction]
    } yield function

    val extensionTargetFunctions = function.extensionMethodOwner
      .flatMap(_.targetTypeElement)
      .flatMap(_.`type`().toOption)
      .collect { case ExtractDesignated(cls: ScTypeDefinition) if cls != containingClass && cls.isWritable => cls }
      .toSeq
      .flatMap(_.members)
      .flatMap(getFunctions)

    val name = function.name
    val functions = classFunctions ++ packageFunctions ++ extensionTargetFunctions
    functions.distinct.filter(_.name == name)
  }
}
