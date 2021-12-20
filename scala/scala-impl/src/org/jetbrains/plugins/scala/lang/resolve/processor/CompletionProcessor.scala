package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.withCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.base.AuxiliaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScType, TermSignature}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.{MappedTopPrecedenceHolder, PrecedenceHelper, TopPrecedenceHolder}

import scala.collection.mutable

object CompletionProcessor {

  def variants(scType: ScType, place: PsiElement, nameHint: Option[String] = None): Set[ScalaResolveResult] = {
    val processor = new CompletionProcessor(StdKinds.methodRef, place, withImplicitConversions = true) {
      override protected val forName: Option[String] = nameHint
    }
    processor.processType(scType, place)
    processor.candidatesS
  }

  def variantsWithName(scType: ScType, place: PsiElement, name: String): Set[ScalaResolveResult] =
    variants(scType, place, Some(name))

  private def findCandidates(element: PsiNamedElement) = element match {
    case AuxiliaryConstructor(_) => Nil // do not add constructor
    case definition: ScTypeDefinition => withCompanionModule(definition)
    case _ => element :: Nil
  }

  private def toSignature(element: PsiNamedElement,
                          substitutor: ScSubstitutor) = element match {
    case method: PsiMethod => Some(new PhysicalMethodSignature(method, substitutor))
    case _: ScTypeAlias |
         _: PsiClass => None
    case _ => Some(TermSignature(element, substitutor))
  }

  private def toResolveResult(element: PsiNamedElement)
                             (implicit state: ResolveState) = new ScalaResolveResult(
    element,
    state.substitutor,
    renamed = state.renamed,
    implicitConversion = state.implicitConversion,
    isNamedParameter = !element.isInstanceOf[ScTypeDefinition] && state.isNamedParameter,
    fromType = state.fromType,
    importsUsed = state.importsUsed,
    prefixCompletion = state.isPrefixCompletion,
    isExtension = state.isExtensionMethod
  )
}

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          override protected val getPlace: PsiElement,
                          val withImplicitConversions: Boolean = false)
  extends BaseProcessor(kinds)(getPlace) with PrecedenceHelper {

  private object CompletionStrategy extends NameUniquenessStrategy {

    override def computeHashCode(result: ScalaResolveResult): Int =
      31 * result.isNamedParameter.hashCode() + super.computeHashCode(result)

    override def equals(left: ScalaResolveResult, right: ScalaResolveResult): Boolean =
      left.isNamedParameter == right.isNamedParameter && super.equals(left, right)
  }

  override def nameUniquenessStrategy: NameUniquenessStrategy = CompletionStrategy

  override protected val holder: TopPrecedenceHolder = new MappedTopPrecedenceHolder(nameUniquenessStrategy)

  private val signatures = mutable.HashSet[TermSignature]()

  val includePrefixImports: Boolean = true

  protected val forName: Option[String] = None

  protected def postProcess(result: ScalaResolveResult): Unit = {
  }

  override protected def isCheckForEqualPrecedence = false

  import CompletionProcessor._

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    if (forName.exists(_ != namedElement.name)) return true

    lazy val predicate: ScalaResolveResult => Boolean = toSignature(namedElement, state.substitutor) match {
      case Some(signature) if state.implicitConversion.isDefined => _ => implicitCase(signature)
      case maybeSignature => regularCase(_, maybeSignature)
    }

    findCandidates(namedElement)
      .filter(ResolveUtils.kindMatches(_, kinds))
      .map(toResolveResult)
      .filter(predicate)
      .foreach(addResult)

    true
  }

  override def changedLevel: Boolean = {
    collectFromLevelSet()

    if (!levelSet.isEmpty) {
      uniqueNamesSet.addAll(levelUniqueNamesSet)
      levelSet.clear()
      levelUniqueNamesSet.clear()
    }

    super.changedLevel
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    collectFromLevelSet()
    candidatesSet
  }


  private def collectFromLevelSet(): Unit = {
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      val next = iterator.next()
      postProcess(next)
      candidatesSet = candidatesSet union Set(next)
    }
  }

  private def regularCase(result: ScalaResolveResult,
                          maybeSignature: Option[TermSignature]): Boolean = {
    signatures ++= maybeSignature

    if (result.prefixCompletion) {
      levelSet.remove(result)
    }

    !levelSet.contains(result)
  }

  private def implicitCase(signature: TermSignature): Boolean =
    signatures.add(signature)
}
