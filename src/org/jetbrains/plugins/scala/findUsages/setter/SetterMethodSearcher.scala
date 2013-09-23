package org.jetbrains.plugins.scala
package findUsages
package setter

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScFunction}
import com.intellij.psi.search.{SearchScope, TextOccurenceProcessor, PsiSearchHelper, UsageSearchContext}
import extensions.{inReadAction, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignStmt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper


class SetterMethodSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  private val suffixScala = "_="
  private val suffixJava = "_$eq"

  def execute(queryParameters: ReferencesSearch.SearchParameters, cons: Processor[PsiReference]): Boolean = {
    inReadAction {
      implicit val scope = queryParameters.getEffectiveSearchScope
      implicit val consumer = cons
      val element = queryParameters.getElementToSearch
      if (element.isValid) {
        element match {
          case fun: ScFunction if fun.name endsWith suffixScala =>
            processAssignments(fun, fun.name)
            processSimpleUsages(fun, fun.name)
          case refPattern: ScReferencePattern if ScalaPsiUtil.nameContext(refPattern).isInstanceOf[ScVariable] =>
            val name = refPattern.name
            processAssignments(refPattern, name)
            processSimpleUsages(refPattern, name + suffixScala)
            processSimpleUsages(refPattern, name + suffixJava)
          case _ =>
        }
      }
    }
    true

  }

  private def processAssignments(element: PsiElement, name: String)(implicit consumer: Processor[PsiReference], scope: SearchScope) = {
    val processor = new TextOccurenceProcessor {
      def execute(elem: PsiElement, offsetInElement: Int): Boolean = {
        elem match {
          case Parent(Parent(assign: ScAssignStmt)) => assign.resolveAssignment match {
            case Some(res) if res.element.getNavigationElement == element =>
              Option(assign.getLExpression).foreach {
                case ref: ScReferenceElement => if (!consumer.process(ref)) return false
              }
            case _ =>
          }
          case _ =>
        }
        true
      }
    }
    val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(element.getProject)
    helper.processElementsWithWord(processor, scope, name.stripSuffix(suffixScala), UsageSearchContext.IN_CODE, true)
  }

  private def processSimpleUsages(element: PsiElement, name: String)(implicit consumer: Processor[PsiReference], scope: SearchScope) = {
    val processor = new TextOccurenceProcessor {
      def execute(elem: PsiElement, offsetInElement: Int): Boolean = {
        elem match {
          case ref: PsiReference => ref.resolve() match {
            case fakeMethod: FakePsiMethod if fakeMethod.navElement == element =>
              if (!consumer.process(ref)) return false
            case wrapper: PsiTypedDefinitionWrapper if wrapper.typedDefinition == element =>
              if (!consumer.process(ref)) return false
            case _ =>
          }
          case _ =>
        }
        true
      }
    }
    val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(element.getProject)
    helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
  }
}
