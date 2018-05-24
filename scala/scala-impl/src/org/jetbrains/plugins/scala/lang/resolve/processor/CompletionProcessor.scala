package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, Signature}
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.{PrecedenceHelper, TopPrecedenceHolder, TopPrecedenceHolderImpl}

import scala.collection.{Set, mutable}

object CompletionProcessor {

  private def getSignature(element: PsiNamedElement, substitutor: ScSubstitutor): Option[Signature] = element match {
    case method: PsiMethod => Some(new PhysicalSignature(method, substitutor))
    case _: ScTypeAlias |
         _: PsiClass => None
    case _ => Some(Signature(element, substitutor))
  }

  private def findByKey[T](key: Key[T])
                          (implicit state: ResolveState): Option[T] =
    Option(state.get(key))

  private def createResolveResults(candidates: Seq[(PsiNamedElement, Boolean)],
                                   substitutor: ScSubstitutor,
                                   implcitConversion: Option[ScalaResolveResult])
                                  (implicit state: ResolveState): Seq[ScalaResolveResult] = {
    val isRenamed = findByKey(ResolverEnv.nameKey)
    val fromType = findByKey(BaseProcessor.FROM_TYPE_KEY)
    val importsUsed = findByKey(ImportUsed.key).getOrElse(Set.empty)
    val prefixCompletion = findByKey(ScalaCompletionUtil.PREFIX_COMPLETION_KEY).getOrElse(false)

    candidates.map {
      case (element, isNamedParameter) => new ScalaResolveResult(element, substitutor,
        nameShadow = isRenamed, implicitConversion = implcitConversion,
        isNamedParameter = isNamedParameter, fromType = fromType,
        importsUsed = importsUsed, prefixCompletion = prefixCompletion)
    }
  }
}

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          override val getPlace: PsiElement,
                          val isImplicit: Boolean = false)
  extends BaseProcessor(kinds)(getPlace) with PrecedenceHelper[(String, Boolean)] {

  override protected val holder: TopPrecedenceHolder[(String, Boolean)] = new TopPrecedenceHolderImpl[(String, Boolean)] {

    override implicit def toRepresentation(result: ScalaResolveResult): (String, Boolean) =
      (result, result.isNamedParameter)
  }

  private val signatures = mutable.HashSet[Signature]()

  val includePrefixImports: Boolean = true

  protected val forName: Option[String] = None

  protected def postProcess(result: ScalaResolveResult): Unit = {
  }

  override protected def isCheckForEqualPrecedence = false

  import CompletionProcessor._

  def execute(element: PsiElement, state: ResolveState): Boolean =
    element match {
      case namedElement: PsiNamedElement =>
        forName match {
          case Some(name) if namedElement.name != name =>
          case _ => execute(namedElement)(state)
        }

        true
      case _ => false
    }

  private def execute(namedElement: PsiNamedElement)
                     (implicit state: ResolveState): Unit = {
    val candidates = findCandidates(namedElement)
    if (candidates.isEmpty) return

    val substitutor = findByKey(ScSubstitutor.key).getOrElse(ScSubstitutor.empty)
    val implicitFunction = findByKey(CachesUtil.IMPLICIT_FUNCTION)

    val resolveResults = createResolveResults(candidates, substitutor, implicitFunction)
    val maybeSignature = getSignature(namedElement, substitutor)

    resolveResults.filter {
      case _ if implicitFunction.isDefined && maybeSignature.isDefined => implicitCase(maybeSignature.get)
      case result => regularCase(result, maybeSignature)
    }.foreach(addResult)
  }

  private def findCandidates(namedElement: PsiNamedElement)
                            (implicit state: ResolveState): Seq[(PsiNamedElement, Boolean)] = {
    val results = namedElement match {
      case function: ScFunction if function.isConstructor =>
        Seq.empty // do not add constructor
      case definition: ScTypeDefinition =>
        (Seq(definition) ++ getCompanionModule(definition)).map((_, false))
      case _ =>
        val isNamedParameter = findByKey(CachesUtil.NAMED_PARAM_KEY).exists(_.booleanValue())
        Seq((namedElement, isNamedParameter))
    }

    results.filter {
      case (e, _) => kindMatches(e)
    }
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

  private def regularCase(result: ScalaResolveResult,
                          maybeSignature: Option[Signature]): Boolean = {
    signatures ++= maybeSignature

    if (result.prefixCompletion) {
      levelSet.remove(result)
    }

    !levelSet.contains(result)
  }

  private def implicitCase(signature: Signature): Boolean =
    signatures.add(signature)
}
