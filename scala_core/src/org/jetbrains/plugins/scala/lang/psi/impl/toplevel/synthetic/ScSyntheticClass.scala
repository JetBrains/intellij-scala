package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import api.toplevel.ScNamedElement
import api.statements.ScFunction
import types._
import resolve._

import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement

import _root_.scala.collection.mutable.{ListBuffer, Map, HashMap, Set, HashSet, MultiMap}

abstract class SyntheticNamedElement(val manager: PsiManager, name: String)
extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with PsiNamedElement {
  def getName = name
  def setName(newName: String) = throw new IncorrectOperationException("nonphysical element")
  def copy = throw new IncorrectOperationException("nonphysical element")
  def accept(v: PsiElementVisitor) = throw new IncorrectOperationException("should not call")
  override def getContainingFile = SyntheticClasses.get(manager.getProject).file
}

// we could try and implement all type system related stuff
// with class types, but it is simpler to indicate types corresponding to synthetic classes explicitly
class ScSyntheticClass(manager: PsiManager, val name: String, val t: ScType)
extends SyntheticNamedElement(manager, name) with PsiClass with PsiClassFake {

  def getText = "" //todo

  override def toString = "Synthetic class"

  object methods extends HashMap[String, Set[ScSyntheticFunction]] with MultiMap[String, ScSyntheticFunction]

  def addMethod(method: ScSyntheticFunction) = methods.add (method.name, method)

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    processor match {
      case p : ResolveProcessor => {
        val nameSet = state.get(ResolverEnv.nameKey)
        val name = if (nameSet == null) p.name else nameSet
        methods.get(name) match {
          case Some(ms) => for (method <- ms) {
            if (!processor.execute(method, state)) return false
          }
          case None =>
        }
      }
      case _ => //todo do we want to complete those?
    }

    true
  }
}

class ScSyntheticFunction(manager: PsiManager, val name: String, val ret: ScType, val params: Seq[ScType])
extends SyntheticNamedElement(manager, name) { //todo provide function interface

  def getText = "" //todo

  override def toString = "Synthetic method"
}

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

object SyntheticClasses {
  def get(project: Project) = project.getComponent(classOf[SyntheticClasses])
}

class SyntheticClasses(project: Project) extends ProjectComponent {
  def projectOpened() {
  }
  def projectClosed() {
  }
  def getComponentName = "SyntheticClasses"
  def disposeComponent() {
  }
  def initComponent() {
    all = new HashMap[String, ScSyntheticClass]
    file = PsiFileFactory.getInstance(project).createFileFromText(
    "dummy." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), "")

    //todo add methods
    registerClass(Any, "Any")
    registerClass(AnyRef, "AnyRef")
    registerClass(AnyVal, "AnyVal")
    registerClass(Nothing, "Nothing")
    registerClass(Null, "Null")
    registerClass(Singleton, "Singleton")
    registerClass(Unit, "Unit")
    registerClass(Boolean, "Boolean")

    registerIntegerClass(registerNumericClass(registerClass(Char, "Char")))
    registerIntegerClass(registerNumericClass(registerClass(Int, "Int")))
    registerIntegerClass(registerNumericClass(registerClass(Long, "Long")))
    registerIntegerClass(registerNumericClass(registerClass(Byte, "Byte")))
    registerIntegerClass(registerNumericClass(registerClass(Short, "Short")))
    registerNumericClass(registerClass(Float, "Float"))
    registerNumericClass(registerClass(Double, "Double"))

    for(ic <- numeric) {
      for (ic1 <- numeric; op <- numeric_comp_ops)
        ic.addMethod(new ScSyntheticFunction(ic.manager, op, Boolean, Seq.singleton(ic1.t)))
      for (ic1 <- numeric; op <- numeric_arith_ops)
        ic.addMethod(new ScSyntheticFunction(ic.manager, op, op_type(ic, ic1), Seq.singleton(ic1.t)))
    }
  }

  var all: Map[String, ScSyntheticClass] = _
  var numeric: Set[ScSyntheticClass] = _
  var integer : Set[ScSyntheticClass] = _
  def op_type (ic1 : ScSyntheticClass, ic2 : ScSyntheticClass) = (ic1.t, ic2.t) match {
    case (_, Double) | (Double, _) => Double
    case (Float, _) | (_, Float) => Float
    case (_, Long) | (Long, _)=> Long
    case _ => Int
  }

  var file : PsiFile = _

  def registerClass(t: ScType, name: String) = {
    var clazz = new ScSyntheticClass(PsiManager.getInstance(project), name, t)
    all + ((name, clazz)); clazz
  }

  def registerIntegerClass(clazz : ScSyntheticClass) = {integer + clazz; clazz}
  def registerNumericClass(clazz : ScSyntheticClass) = {numeric + clazz; clazz}


  def getAll() = all.values.toList.toArray

  def byName(name: String) = all.get(name) match {
    case Some(c) => c
    case _ => null
  }

  val numeric_comp_ops = "==" :: "!=" :: "<" :: ">" :: "<=" :: ">=" :: Nil
  val numeric_arith_ops = "+" :: "-" :: "*" :: "/" :: "%" :: Nil
}