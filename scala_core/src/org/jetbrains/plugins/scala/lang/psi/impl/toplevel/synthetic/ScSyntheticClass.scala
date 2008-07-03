package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import api.toplevel.ScNamedElement
import api.statements.ScFunction
import types._

import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement

import _root_.scala.collection.mutable.{ListBuffer, Map, HashMap}

abstract class SyntheticNamedElement(manager: PsiManager, name: String)
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

  val methods = new ListBuffer[ScSyntheticFunction]

  def addMethod(method: ScSyntheticFunction) = methods += method

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (method <- methods) {
      if (!processor.execute(method, state)) return false
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
    m = new HashMap[String, ScSyntheticClass]
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
    registerClass(Char, "Char")
    registerClass(Int, "Int")
    registerClass(Long, "Long")
    registerClass(Float, "Float")
    registerClass(Double, "Double")
    registerClass(Byte, "Byte")
    registerClass(Short, "Short")
  }

  var m: Map[String, ScSyntheticClass] = _
  var file : PsiFile = _

  def registerClass(t: ScType, name: String) {
    m + ((name, new ScSyntheticClass(PsiManager.getInstance(project), name, t)))
  }

  def getAll() = m.values.toList.toArray

  def byName(name: String) = m.get(name) match {
    case Some(c) => c
    case _ => null
  }
}