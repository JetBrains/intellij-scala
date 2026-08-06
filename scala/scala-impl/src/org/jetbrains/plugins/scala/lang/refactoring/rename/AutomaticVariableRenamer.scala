package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.naming.{AutomaticRenamer, AutomaticRenamerFactory}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPatternLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ExtractDesignated, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes.TypePluralNamesProvider
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.util
import scala.annotation.tailrec

final class AutomaticVariableRenamer(
  cls: PsiNamedElement,
  newName: String,
  usages: util.Collection[UsageInfo],
) extends AutomaticRenamer {
  private val toUnpluralize = new java.util.ArrayList[ScNamedElement]

  locally {
    val oldClassName = cls.name

    usages.forEach { usage =>
      usage.getElement match {
        case ref: ScReference =>
          val parent = PsiTreeUtil.getParentOfType(ref,
            /* parents */ classOf[ScParameter], classOf[ScValueOrVariable], classOf[ScTypedPatternLike],
            /* stop at */ classOf[ScExpression])
          val elements: Seq[ScTypedDefinition] = parent match {
            case param: ScParameter => Seq(param)
            case valueOrVariable: ScValueOrVariable => valueOrVariable.declaredElements
            case typedPattern: ScTypedPatternLike => typedPattern.bindings
            case _ => Seq.empty
          }
          elements.foreach { element =>
            val variableName = element.name
            if (!variableName.equalsIgnoreCase(newName) && StringUtil.containsIgnoreCase(variableName, oldClassName)) {
              if (element.`type`().toOption.exists(isCollectionLikeOfRenamedClass)) {
                toUnpluralize.add(element)
              }

              myElements.add(element)
            }
          }
        case _ =>
      }
    }

    suggestAllNames(oldClassName, newName)
  }

  override def nameToCanonicalName(name: String, element: PsiNamedElement): String = element match {
    case namedElement: ScNamedElement if toUnpluralize.contains(namedElement) =>
      val singular = StringUtil.unpluralize(name)
      if (singular != null) singular
      else {
        toUnpluralize.remove(namedElement)
        name
      }
    case _ => name
  }

  override def canonicalNameToName(canonicalName: String, element: PsiNamedElement): String = element match {
    case namedElement: ScNamedElement if toUnpluralize.contains(namedElement) =>
      StringUtil.pluralize(canonicalName)
    case _ => canonicalName
  }

  override def entityName(): String = JavaRefactoringBundle.message("entity.name.variable")

  override def getDialogTitle: String = JavaRefactoringBundle.message("rename.variables.title")

  override def getDialogDescription: String = JavaRefactoringBundle.message("title.rename.variables.with.the.following.names.to")

  @tailrec
  private def isCollectionLikeOfRenamedClass(elementType: ScType): Boolean = elementType match {
    case TypePluralNamesProvider.IsTraversable(_, argument) =>
      argument match {
        case ExtractDesignated(`cls`) => true
        case _ => isCollectionLikeOfRenamedClass(argument)
      }
    case _ => false
  }
}

final class AutomaticVariableRenamerFactory extends AutomaticRenamerFactory {
  override def isApplicable(element: PsiElement): Boolean = element.is[PsiClass, ScTypeAlias]

  override def createRenamer(element: PsiElement, newName: String, usages: util.Collection[UsageInfo]): AutomaticRenamer =
    new AutomaticVariableRenamer(element.asInstanceOf[PsiNamedElement], newName, usages)

  override def isEnabled: Boolean = ScalaApplicationSettings.getInstance().RENAME_VARIABLES

  override def setEnabled(enabled: Boolean): Unit = ScalaApplicationSettings.getInstance().RENAME_VARIABLES = enabled

  override def getOptionName: String = JavaRefactoringBundle.message("rename.variables")
}
