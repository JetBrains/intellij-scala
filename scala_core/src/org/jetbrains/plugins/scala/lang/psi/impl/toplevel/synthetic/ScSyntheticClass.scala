package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import api.statements.params.ScTypeParam
import api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import com.intellij.openapi.startup.StartupManager
import com.intellij.psi.search.GlobalSearchScope
import api.statements.ScFun
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
  def getText = ""
  def setName(newName: String) : PsiElement = throw new IncorrectOperationException("nonphysical element")
  def copy = throw new IncorrectOperationException("nonphysical element")
  def accept(v: PsiElementVisitor) = throw new IncorrectOperationException("should not call")
  override def getContainingFile = SyntheticClasses.get(manager.getProject).file

  def nameId(): PsiElement = null
  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = Array[T]()
  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = null
}

class ScSyntheticTypeParameter(manager: PsiManager, name: String, val owner: ScFun)
extends SyntheticNamedElement(manager, name) with ScTypeParam with PsiClassFake {
  override def toString = "Synthetic type parameter"

  def isCovariant() = false
  def isContravariant() = false
  def lowerBound() = Nothing
  def upperBound() = Any

  def getIndex = -1
  def getOwner = null
}
// we could try and implement all type system related stuff
// with class types, but it is simpler to indicate types corresponding to synthetic classes explicitly
class ScSyntheticClass(manager: PsiManager, val name: String, val t: ScType)
extends SyntheticNamedElement(manager, name) with PsiClass with PsiClassFake {

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
      case _ =>
        for(p <- methods; method <- p._2) {
          if (!processor.execute(method, state)) return false
        }
    }

    true
  }
}

class ScSyntheticFunction(manager: PsiManager, val name: String,
                          val retType: ScType, val paramTypes: Seq[ScType],
                          typeParameterNames : Seq[String])
extends SyntheticNamedElement(manager, name) with ScFun {
  def this(manager: PsiManager, name: String, retType: ScType, paramTypes: Seq[ScType]) =
    this(manager, name, retType, paramTypes, Seq.empty)

  val typeParams = typeParameterNames.map {name => new ScSyntheticTypeParameter(manager, name, this)}
  override def typeParameters = typeParams

  override def getIcon(flags: Int) = icons.Icons.METHOD

  override def toString = "Synthetic method"
}

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

object SyntheticClasses {
  def get(project: Project) = project.getComponent(classOf[SyntheticClasses])
}

class SyntheticClasses(project: Project) extends ProjectComponent with PsiElementFinder {
  def projectOpened {}
  def projectClosed {}
  def getComponentName = "SyntheticClasses"
  def disposeComponent {}

  def initComponent() {
    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable {
      def run = registerClasses
    })
  }

  def registerClasses = {
    all = new HashMap[String, ScSyntheticClass]
    file = PsiFileFactory.getInstance(project).createFileFromText(
    "dummy." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), "")

    val any = registerClass(Any, "Any")
    any.addMethod(new ScSyntheticFunction(any.manager, "isInstanceOf", Boolean, Seq.empty, Seq.singleton("T")))
    any.addMethod(new ScSyntheticFunction(any.manager, "asInstanceOf", Any, Seq.empty, Seq.singleton("T")) {
      override val retType = ScalaPsiManager.typeVariable(typeParams(0))
    })

    val anyRef = registerClass(AnyRef, "AnyRef")
    anyRef.addMethod(new ScSyntheticFunction(anyRef.manager, "eq", Boolean, Seq.singleton(AnyRef)))
    anyRef.addMethod(new ScSyntheticFunction(anyRef.manager, "ne", Boolean, Seq.singleton(AnyRef)))

    registerClass(AnyVal, "AnyVal")
    registerClass(Nothing, "Nothing")
    registerClass(Null, "Null")
    registerClass(Singleton, "Singleton")
    registerClass(Unit, "Unit")

    val boolc = registerClass(Boolean, "Boolean")
    for (op <- bool_bin_ops)
      boolc.addMethod(new ScSyntheticFunction(boolc.manager, op, Boolean, Seq.singleton(Boolean)))
    boolc.addMethod(new ScSyntheticFunction(boolc.manager, "!", Boolean, Seq.empty))

    registerIntegerClass(registerNumericClass(registerClass(Char, "Char")))
    registerIntegerClass(registerNumericClass(registerClass(Int, "Int")))
    registerIntegerClass(registerNumericClass(registerClass(Long, "Long")))
    registerIntegerClass(registerNumericClass(registerClass(Byte, "Byte")))
    registerIntegerClass(registerNumericClass(registerClass(Short, "Short")))
    registerNumericClass(registerClass(Float, "Float"))
    registerNumericClass(registerClass(Double, "Double"))

    for(nc <- numeric) {
      for (nc1 <- numeric; op <- numeric_comp_ops)
        nc.addMethod(new ScSyntheticFunction(nc.manager, op, Boolean, Seq.singleton(nc1.t)))
      for (nc1 <- numeric; op <- numeric_arith_ops)
        nc.addMethod(new ScSyntheticFunction(nc.manager, op, op_type(nc, nc1), Seq.singleton(nc1.t)))
      for (nc1 <- numeric if nc1 ne nc)
        nc.addMethod(new ScSyntheticFunction(nc.manager, "to" + nc1.name, nc1.t, Seq.empty))
      for (un_op <- numeric_arith_unary_ops)
        nc.addMethod(new ScSyntheticFunction(nc.manager, un_op, nc.t, Seq.empty))
    }

    for (ic <- integer) {
      for (ic1 <- integer; op <- bitwise_bin_ops)
        ic.addMethod(new ScSyntheticFunction(ic.manager, op, op_type(ic, ic1), Seq.singleton(ic1.t)))
      ic.addMethod(new ScSyntheticFunction(ic.manager, "~", ic.t, Seq.empty))

      val ret = ic.t match {
        case Long => Long
        case _ => Int
      }
      for (op <- bitwise_shift_ops) {
        ic.addMethod(new ScSyntheticFunction(ic.manager, op, ret, Seq.singleton(Int)))
        ic.addMethod(new ScSyntheticFunction(ic.manager, op, ret, Seq.singleton(Long)))
      }
    }
  }

  var all: Map[String, ScSyntheticClass] = new HashMap[String, ScSyntheticClass]
  var numeric: Set[ScSyntheticClass] = new HashSet[ScSyntheticClass]
  var integer : Set[ScSyntheticClass] = new HashSet[ScSyntheticClass]
  def op_type (ic1 : ScSyntheticClass, ic2 : ScSyntheticClass) = (ic1.t, ic2.t) match {
    case (_, Double) | (Double, _) => Double
    case (Float, _) | (_, Float) => Float
    case (_, Long) | (Long, _)=> Long
    case _ => Int
  }

  var file : PsiFile = _

  def registerClass(t: ScType, name: String) = {
    val manager = PsiManager.getInstance(project)
    var clazz = new ScSyntheticClass(manager, name, t)
    clazz.addMethod(new ScSyntheticFunction(manager, "equals", Boolean, Seq.singleton(Any)))
    clazz.addMethod(new ScSyntheticFunction(manager, "==", Boolean, Seq.singleton(Any)))
    clazz.addMethod(new ScSyntheticFunction(manager, "!=", Boolean, Seq.singleton(Any)))
    clazz.addMethod(new ScSyntheticFunction(manager, "hashCode", Int, Seq.empty))
    val stringClass = JavaPsiFacade.getInstance(project).findClass("java.lang.String", GlobalSearchScope.allScope(project))
    if (stringClass != null) {
      val stringType = new ScDesignatorType(stringClass)
      clazz.addMethod(new ScSyntheticFunction(manager, "toString", stringType, Seq.empty))
    }

    all + ((name, clazz)); clazz
  }

  def registerIntegerClass(clazz : ScSyntheticClass) = {integer += clazz; clazz}
  def registerNumericClass(clazz : ScSyntheticClass) = {numeric += clazz; clazz}


  def getAll() = all.values.toList.toArray

  def byName(name: String) = all.get(name)

  val numeric_comp_ops = "==" :: "!=" :: "<" :: ">" :: "<=" :: ">=" :: Nil
  val numeric_arith_ops = "+" :: "-" :: "*" :: "/" :: "%" :: Nil
  val numeric_arith_unary_ops = "+" :: "-" :: Nil
  val bool_bin_ops = "&&" :: "||" :: "&" :: "|" :: "==" :: "!=" :: Nil
  val bitwise_bin_ops = "&" :: "|" :: "^" :: Nil
  val bitwise_shift_ops = "<<" :: ">>" :: ">>>" :: Nil

  val prefix = "scala."
  def findClass(qName : String, scope : GlobalSearchScope) = if (qName.startsWith(prefix)) {
    byName(qName.substring(prefix.length)) match {
      case Some(c) => c
      case _ => null
    }
  } else null

  def findClasses(qName : String, scope : GlobalSearchScope) = {
    val c = findClass(qName, scope)
    if (c == null) PsiClass.EMPTY_ARRAY else Array(c)
  }

  def findPackage(qName : String) = null

  def getSubPackages(p : PsiPackage, scope : GlobalSearchScope) = Array[PsiPackage]()

  def getClasses(p : PsiPackage, scope : GlobalSearchScope) = findClasses(p.getQualifiedName, scope)
}