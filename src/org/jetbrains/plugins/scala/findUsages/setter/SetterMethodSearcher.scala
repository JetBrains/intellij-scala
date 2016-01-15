package org.jetbrains.plugins.scala
package findUsages
package setter

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, SearchScope, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.{Parent, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper


class SetterMethodSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  private val suffixScala = "_="
  private val suffixJava = "_$eq"

  def execute(queryParameters: ReferencesSearch.SearchParameters, cons: Processor[PsiReference]): Boolean = {
    implicit val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    implicit val consumer = cons
    val element = queryParameters.getElementToSearch
    val project = queryParameters.getProject
    element match {
      case _ if !inReadAction(element.isValid) => true
      case fun: ScFunction if fun.name endsWith suffixScala =>
        processAssignments(fun, fun.name, project)
        processSimpleUsages(fun, fun.name, project)
      case refPattern: ScReferencePattern if inReadAction(ScalaPsiUtil.nameContext(refPattern)).isInstanceOf[ScVariable] =>
        val name = refPattern.name
        processAssignments(refPattern, name, project)
        processSimpleUsages(refPattern, name + suffixScala, project)
        processSimpleUsages(refPattern, name + suffixJava, project)
      case _ => true
    }
  }

  private def processAssignments(element: PsiElement, name: String, project: Project)(implicit consumer: Processor[PsiReference], scope: SearchScope) = {
    val processor = new TextOccurenceProcessor {
      def execute(elem: PsiElement, offsetInElement: Int): Boolean = {
        inReadAction {
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
    }
    val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(project)
    helper.processElementsWithWord(processor, scope, name.stripSuffix(suffixScala), UsageSearchContext.IN_CODE, true)
  }

  private def processSimpleUsages(element: PsiElement, name: String, project: Project)(implicit consumer: Processor[PsiReference], scope: SearchScope) = {
    val processor = new TextOccurenceProcessor {
      def execute(elem: PsiElement, offsetInElement: Int): Boolean = {
        inReadAction {
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
        }
        true
      }
    }
    val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(project)
    helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
  }
}
