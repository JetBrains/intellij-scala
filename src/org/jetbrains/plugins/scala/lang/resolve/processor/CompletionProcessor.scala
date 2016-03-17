package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScSubstitutor, ScType, Signature}
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor.QualifiedName

import scala.collection.{Set, mutable}

object CompletionProcessor {
  case class QualifiedName(name: String, isNamedParameter: Boolean)

  def getQualifiedName(result: ScalaResolveResult): QualifiedName = {
    val name = result.isRenamed match {
      case Some(str) => str
      case None => result.name
    }
    val isNamedParameter = result.isNamedParameter

    QualifiedName(name, isNamedParameter)
  }
}

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          val place: PsiElement,
                          val collectImplicits: Boolean = false,
                          forName: Option[String] = None,
                          postProcess: ScalaResolveResult => Unit = r => {},
                          val includePrefixImports: Boolean = true)
                         (implicit override val typeSystem: TypeSystem)
  extends BaseProcessor(kinds) with PrecedenceHelper[QualifiedName] {
  protected val precedence: mutable.HashMap[QualifiedName, Int] = new mutable.HashMap[QualifiedName, Int]()

  protected val signatures: mutable.HashMap[Signature, Boolean] = new mutable.HashMap[Signature, Boolean]()

  protected def getPlace: PsiElement = place

  protected def getQualifiedName(result: ScalaResolveResult): QualifiedName = CompletionProcessor.getQualifiedName(result)

  override protected def isCheckForEqualPrecedence = false

  protected def getTopPrecedence(result: ScalaResolveResult): Int = precedence.getOrElse(getQualifiedName(result), 0)

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence.put(getQualifiedName(result), i)
  }

  override protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean = {
    getQualifiedName(p) == getQualifiedName(n) && super.filterNot(p, n)
  }

  def getSignature(element: PsiNamedElement, substitutor: => ScSubstitutor): Option[Signature] = {
    element match {
      case method: PsiMethod => Some(new PhysicalSignature(method, substitutor))
      case td: ScTypeAlias => None
      case td: PsiClass => None
      case _ => Some(new Signature(element.name, Seq.empty, 0, substitutor, element))
    }
  }

  def execute(_element: PsiElement, state: ResolveState): Boolean = {
    if (!_element.isInstanceOf[PsiElement]) return false
    val element = _element.asInstanceOf[PsiNamedElement]
    forName match {
      case Some(name) if element.name != name => return true
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
      val signature: Option[Signature] = getSignature(element, substitutor)
      val forImplicit = implFunction.isDefined
      if (!forImplicit) {
        if (levelSet.contains(result)) {
          if (result.prefixCompletion) {
            levelSet.remove(result)
            addResult(result)
          }
        } else addResult(result)
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
                signature.foreach(sign => signatures += ((sign, forImplicit)))
            }
          case _ =>
            if (levelSet.contains(result)) {
              if (result.prefixCompletion) {
                levelSet.remove(result)
                addResult(result)
              }
            } else addResult(result)
        }
      }
    }

    element match {
      case fun: ScFunction if fun.isConstructor => return true //do not add constructor
      case td: ScTypeDefinition =>
        if (kindMatches(td)) {
          val result = new ScalaResolveResult(td, substitutor, nameShadow = isRenamed,
            implicitFunction = implFunction, fromType = fromType, importsUsed = importsUsed, prefixCompletion = prefixCompletion)
          _addResult(result)
        }
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) if kindMatches(td) =>
            val result = new ScalaResolveResult(td, substitutor,
              nameShadow = isRenamed, implicitFunction = implFunction, fromType = fromType, importsUsed = importsUsed,
              prefixCompletion = prefixCompletion)
            _addResult(result)
          case _ =>
        }
      case named: PsiNamedElement =>
        if (kindMatches(element)) {
          element match {
            case method: PsiMethod =>
              val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType,
                importsUsed = importsUsed, prefixCompletion = prefixCompletion)
              _addResult(result)
            case bindingPattern: ScBindingPattern =>
              val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType,
                importsUsed = importsUsed, prefixCompletion = prefixCompletion)
              _addResult(result)
            case _ =>
              val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType,
                importsUsed = importsUsed, prefixCompletion = prefixCompletion)
              _addResult(result)
          }
        }
    }
    true
  }

  override def changedLevel: Boolean = {
    if (levelSet.isEmpty) return true
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      val next = iterator.next()
      postProcess(next)
      candidatesSet += next
    }
    qualifiedNamesSet.addAll(levelQualifiedNamesSet)
    levelSet.clear()
    levelQualifiedNamesSet.clear()
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val res = candidatesSet
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      val next = iterator.next()
      postProcess(next)
      res += next
    }
    res
  }
}
