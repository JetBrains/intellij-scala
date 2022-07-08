package org.jetbrains.plugins.scala
package findUsages
package parameters

import com.intellij.find.findUsages.{CustomUsageSearcher, FindUsagesOptions}
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.usages.{Usage, UsageInfoToUsageConverter}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

/**
 * {{{
 *   case class A(/*search*/a: Int)
 *   null match {
 *     case A(x) => /*found*/x
 *   }
 * }}}
 */
class ConstructorParamsInConstructorPatternSearcher extends CustomUsageSearcher {
  override def processElementUsages(element: PsiElement, processor0: Processor[_ >: Usage], options: FindUsagesOptions): Unit = {
    element match {
      case parameterOfClassWithIndex(cls, index) =>
        val scope = inReadAction(element.getUseScope)
        val correspondingSubpatternWithBindings = new SubPatternWithIndexBindings(index)

        val processor = new Processor[PsiReference] {
          override def process(t: PsiReference): Boolean = t match {
            case correspondingSubpatternWithBindings(Seq(only)) =>
              ReferencesSearch.search(only, scope, false).forEach((t: PsiReference) => {
                inReadAction {
                  val descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(Array(), Array(only))
                  val usage = UsageInfoToUsageConverter.convert(descriptor, new UsageInfo(t))
                  processor0.process(usage)
                }
              })
            case _ => true
          }
        }
        ReferencesSearch.search(cls, scope, false).forEach(processor)
      case _ =>
    }
  }

  private object parameterOfClassWithIndex {
    def unapply(param: ScClassParameter): Option[(ScClass, Int)] = {
      inReadAction {
        if (!param.isValid) return None

        param.parentOfType(classOf[ScPrimaryConstructor]).flatMap {
          case pc@ScPrimaryConstructor.ofClass(cls) if cls.isCase =>
            pc.parameters.indexOf(param) match {
              case -1 => None
              case i => Some(cls, i)
            }
          case _ => None
        }
      }
    }
  }

  private class SubPatternWithIndexBindings(i: Int) {
    def unapply(ref: PsiReference): Option[Seq[ScBindingPattern]] = {
      inReadAction {
        ref.getElement.getParent match {
          case consPattern: ScConstructorPattern => consPattern.args.patterns.lift(i).map(_.bindings)
          case _ => None
        }
      }
    }
  }
}