package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.openapi.util.Key
import psi.types.ScSubstitutor

import _root_.scala.collection.Set
import _root_.scala.collection.immutable.HashSet

class ResolveProcessor(override val kinds: Set[ResolveTargets], val name: String) extends BaseProcessor(kinds)
{
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      candidatesSet += new ScalaResolveResult(named, getSubst(state))
      return false //todo: for locals it is ok to terminate the walkup, later need more elaborate check
    }
    return true
  }

  protected def nameAndKindMatch (named: PsiNamedElement, state: ResolveState) = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) named.getName else nameSet
    elName == name && kindMatches(named)
  }

  protected def getSubst(state: ResolveState) = {
    val subst = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  override def getHint[T](hintClass: Class[T]): T =
    if (hintClass == classOf[NameHint] && name != "") ScalaNameHint.asInstanceOf[T]
    else super.getHint(hintClass)

  object ScalaNameHint extends NameHint {
    def getName(state : ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}

class MethodResolveProcessor(override val name : String) extends ResolveProcessor(StdKinds.methodRef, name) {
  override def execute(element: PsiElement, state: ResolveState) : Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val s = getSubst(state)
      element match {
        case m : PsiMethod => {
          candidatesSet += new ScalaResolveResult(named, s.incl(inferMethodTypesArgs(m, s)))
          true
        }
        case _ => candidatesSet += new ScalaResolveResult(named, s); true
      }
    }
    return true
  }

  def inferMethodTypesArgs(m : PsiMethod, classSubst : ScSubstitutor) = ScSubstitutor.empty //todo
}

import ResolveTargets._

object StdKinds {
  val stableQualRef = HashSet.empty[ResolveTargets] + PACKAGE + OBJECT + VAL
  val stableQualOrClass = stableQualRef + CLASS
  val stableImportSelector = HashSet.empty[ResolveTargets] + OBJECT + VAL + VAR + METHOD + PACKAGE + CLASS
  val stableClass = HashSet.empty[ResolveTargets] + CLASS

  val refExprLastRef = HashSet.empty[ResolveTargets] + OBJECT + VAL + VAR + METHOD
  val refExprQualRef = refExprLastRef + PACKAGE

  val methodRef = HashSet.empty[ResolveTargets] + VAL + VAR + METHOD
}

object ResolverEnv {
  val nameKey: Key[String] = Key.create("ResolverEnv.nameKey")
}