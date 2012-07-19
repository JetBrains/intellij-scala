package org.jetbrains.plugins.scala
package findUsages
package parameters

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi._
import search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignStmt}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.extensions.inReadAction
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import lang.psi.api.base.patterns.ScConstructorPattern
import com.intellij.find.findUsages.{FindUsagesOptions, CustomUsageSearcher}
import com.intellij.usageView.UsageInfo
import com.intellij.usages.{UsageInfoToUsageConverter, Usage}

/**
 * {{{
 *   case class A(/*search*/a: Int)
 *   null match {
 *     case A(x) => /*found*/x
 *   }
 * }}}
 *
 * User: Jason Zaugg
 */
class ConstructorParamsInConstructorPatternSearcher extends CustomUsageSearcher {
  def processElementUsages(element: PsiElement, processor0: Processor[Usage], options: FindUsagesOptions) {
    inReadAction {
      val scope = element.getResolveScope
      element match {
        case parameter: ScClassParameter => {
          ScalaPsiUtil.getParentOfType(parameter, classOf[ScClass]) match {
            case cls: ScClass if cls.isCase && cls.constructor.isDefined && cls.constructor.get.parameters.contains(parameter) =>
              val index = cls.constructor.get.parameters.indexOf(parameter)
              object processor extends Processor[PsiReference] {
                def process(t: PsiReference): Boolean = {
                  t.getElement.getParent match {
                    case consPattern: ScConstructorPattern =>
                      consPattern.args.patterns.lift(index) match {
                        case Some(x) =>
                          x.bindings match {
                            case Seq(only) =>
                              ReferencesSearch.search(only, scope, false).forEach(new Processor[PsiReference] {
                                def process(t: PsiReference): Boolean = {
                                  val descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(Array(), Array(only))
                                  val usage = UsageInfoToUsageConverter.convert(descriptor, new UsageInfo(t))
                                  processor0.process(usage)
                                }
                              })
                            case _ =>
                              true
                            // too complex
                          }
                        case None =>
                          true
                      }
                    case _ =>
                      true
                  }
                }
              }
              ReferencesSearch.search(cls, scope, false).forEach(processor)
            case _ =>
          }
        }
        case _ =>
      }
      true
    }
  }
}
