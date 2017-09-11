package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScSubstitutor, ScType, Signature}
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor.QualifiedName
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceHelper

import scala.collection.{Set, mutable}

object CompletionProcessor {

  private def getSignature(element: PsiNamedElement, substitutor: => ScSubstitutor): Option[Signature] = element match {
    case method: PsiMethod => Some(new PhysicalSignature(method, substitutor))
    case _: ScTypeAlias |
         _: PsiClass => None
    case _ => Some(Signature(element, substitutor))
  }

  case class QualifiedName(name: String, isNamedParameter: Boolean)

  object QualifiedName {

    def apply(result: ScalaResolveResult): QualifiedName = {
      val name = result.isRenamed.getOrElse(result.name)
      QualifiedName(name, result.isNamedParameter)
    }
  }

}

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          val getPlace: PsiElement,
                          val collectImplicits: Boolean = false,
                          forName: Option[String] = None,
                          val includePrefixImports: Boolean = true,
                          val isIncomplete: Boolean = true)
  extends BaseProcessor(kinds)(getPlace) with PrecedenceHelper[QualifiedName] {

  private val precedence = new mutable.HashMap[QualifiedName, Int]()

  private val signatures = new mutable.HashMap[Signature, Boolean]()

  protected def postProcess(result: ScalaResolveResult): Unit = {
  }

  protected def getQualifiedName(result: ScalaResolveResult): QualifiedName =
    QualifiedName(result)

  override protected def isCheckForEqualPrecedence = false

  protected def getTopPrecedence(result: ScalaResolveResult): Int =
    precedence.getOrElse(QualifiedName(result), 0)

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int): Unit = {
    precedence.put(QualifiedName(result), i)
  }

  override protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean =
    QualifiedName(p) == QualifiedName(n) && super.filterNot(p, n)

  import CompletionProcessor._

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (!element.isInstanceOf[PsiNamedElement]) return false
    val named = element.asInstanceOf[PsiNamedElement]

    forName match {
      case Some(name) if named.name != name => return true
      case _ =>
    }

    lazy val substitutor: ScSubstitutor = Option(state.get(ScSubstitutor.key)).getOrElse(ScSubstitutor.empty)
    lazy val isRenamed: Option[String] = Option(state.get(ResolverEnv.nameKey))
    lazy val implFunction: Option[PsiNamedElement] = Option(state.get(CachesUtil.IMPLICIT_FUNCTION))
    lazy val isNamedParameter: Boolean = Option(state.get(CachesUtil.NAMED_PARAM_KEY)).exists(_.booleanValue())
    val fromType: Option[ScType] = Option(state.get(BaseProcessor.FROM_TYPE_KEY))
    val importsUsed: Set[ImportUsed] = Option(state.get(ImportUsed.key)).getOrElse(Set.empty)
    val prefixCompletion: Boolean = Option(state.get(ScalaCompletionUtil.PREFIX_COMPLETION_KEY)).getOrElse(false)

    def _addResult(result: ScalaResolveResult) {
      val signature: Option[Signature] = getSignature(named, substitutor)
      val forImplicit = implFunction.isDefined

      def doAdd() = if (levelSet.contains(result)) {
        if (result.prefixCompletion) {
          levelSet.remove(result)
          addResult(result)
        }
      } else addResult(result)

      if (!forImplicit) {
        doAdd()
        signature.foreach(sign => signatures += ((sign, forImplicit)))
      } else {
        signature match {
          case Some(sign) =>
            signatures.get(sign) match {
              case Some(true) =>
                val iterator = levelSet.iterator()
                while (iterator.hasNext) {
                  val next = iterator.next()
                  if (getQualifiedName(next) == getQualifiedName(result) && next.element != result.element &&
                    signature == getSignature(next.element, next.substitutor)) {
                    iterator.remove()
                  }
                }
              case Some(false) => //do nothing
              case None =>
                addResult(result)
                signatures += ((sign, forImplicit))
            }
          case _ => doAdd()
        }
      }
    }

    val results = named match {
      case function: ScFunction if function.isConstructor => Seq.empty // do not add constructor
      case definition: ScTypeDefinition => (Seq(definition) ++ getCompanionModule(definition)).map((_, false))
      case _ => Seq((named, isNamedParameter))
    }

    results.filter {
      case (e, _) => kindMatches(e)
    }.map {
      case (e, f) => new ScalaResolveResult(e, substitutor,
        nameShadow = isRenamed, implicitFunction = implFunction,
        isNamedParameter = f, fromType = fromType,
        importsUsed = importsUsed, prefixCompletion = prefixCompletion)
    }.foreach(_addResult)

    true
  }

  override def changedLevel: Boolean = {
    collectResults(candidatesSet)

    if (!levelSet.isEmpty) {
      qualifiedNamesSet.addAll(levelQualifiedNamesSet)
      levelSet.clear()
      levelQualifiedNamesSet.clear()
    }

    super.changedLevel
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    collectResults(candidatesSet)
    candidatesSet
  }


  private def collectResults(accumulator: mutable.HashSet[ScalaResolveResult]): Unit = {
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      val next = iterator.next()
      postProcess(next)
      accumulator.add(next)
    }
  }
}
