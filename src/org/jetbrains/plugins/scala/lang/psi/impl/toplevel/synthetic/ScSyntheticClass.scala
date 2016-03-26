package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic

import _root_.javax.swing.Icon

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.startup.StartupManager
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFun
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ImplicitProcessor, ResolveProcessor, ResolverEnv}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScalaUtils

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, mutable}

abstract class SyntheticNamedElement(val manager: PsiManager, name: String)
extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with PsiNameIdentifierOwner {
  override def getName = name
  override def getText = ""
  def setName(newName: String) : PsiElement = throw new IncorrectOperationException("nonphysical element")
  override def copy = throw new IncorrectOperationException("nonphysical element")
  override def accept(v: PsiElementVisitor) {
    throw new IncorrectOperationException("should not call")
  }
  override def getContainingFile = SyntheticClasses.get(manager.getProject).file

  def nameId: PsiElement = null
  override def getNameIdentifier: PsiIdentifier = null
}

class ScSyntheticTypeParameter(manager: PsiManager, override val name: String, val owner: ScFun)
extends SyntheticNamedElement(manager, name) with ScTypeParam with PsiClassFake {
  def typeParameterText: String = name

  override def getPresentation: ItemPresentation = super[ScTypeParam].getPresentation

  def getPsiElementId: PsiElement = null

  def getOffsetInFile: Int = 0

  def getContainingFileName: String = "NoFile"

  override def toString = "Synthetic type parameter: " + name

  def isCovariant = false
  def isContravariant = false
  def lowerBound = Success(Nothing, Some(this))
  def upperBound = Success(Any, Some(this))

  def getIndex = -1
  def getOwner = null

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)
}
// we could try and implement all type system related stuff
// with class types, but it is simpler to indicate types corresponding to synthetic classes explicitly
class ScSyntheticClass(manager: PsiManager, val className: String, val t: StdType)
extends SyntheticNamedElement(manager, className) with PsiClass with PsiClassFake {
  override def getPresentation: ItemPresentation = {
    new ItemPresentation {
      val This = ScSyntheticClass.this
      def getLocationString: String = "(scala)"

      def getTextAttributesKey: TextAttributesKey = null

      def getPresentableText: String = This.className

      def getIcon(open: Boolean): Icon = This.getIcon(0)
    }
  }

  override def toString = "Synthetic class"

  def syntheticMethods(scope: GlobalSearchScope) = methods.values.flatMap(s => s).toList ++
          specialMethods.values.flatMap(s => s.map(_(scope))).toList

  protected object methods extends mutable.HashMap[String, mutable.Set[ScSyntheticFunction]] with mutable.MultiMap[String, ScSyntheticFunction]
  protected object specialMethods extends mutable.HashMap[String, mutable.Set[GlobalSearchScope => ScSyntheticFunction]] with
          mutable.MultiMap[String, GlobalSearchScope => ScSyntheticFunction]

  def addMethod(method: ScSyntheticFunction) = methods.addBinding(method.name, method)
  def addMethod(method: GlobalSearchScope => ScSyntheticFunction, methodName: String) = specialMethods.addBinding(methodName, method)

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    processor match {
      case p : ResolveProcessor =>
        val nameSet = state.get(ResolverEnv.nameKey)
        val name = if (nameSet == null) p.name else nameSet
        methods.get(name) match {
          case Some(ms) => for (method <- ms) {
            if (!processor.execute(method, state)) return false
          }
          case None =>
        }
      case _: ImplicitProcessor => //do nothing, there is no implicit synthetic methods
      case _: BaseProcessor =>
        //method toString and hashCode exists in java.lang.Object
        for (p <- methods; method <- p._2) {
          if (!processor.execute(method, state)) return false
        }
      case _ => //do not execute synthetic methods to not Scala processors.
    }

    true
  }

  override def getSuperTypes: Array[PsiClassType] = {
    val project = manager.getProject
    t.tSuper match {
      case None => PsiClassType.EMPTY_ARRAY
      case Some(ts) => Array[PsiClassType] (JavaPsiFacade.getInstance(project).getElementFactory.
              createType(ts.asClass(project).getOrElse(return PsiClassType.EMPTY_ARRAY), PsiSubstitutor.EMPTY))
    }
  }
}

class ScSyntheticFunction(manager: PsiManager, val name: String,
                          val retType: ScType, val paramClauses: Seq[Seq[Parameter]],
                          typeParameterNames : Seq[String])
extends SyntheticNamedElement(manager, name) with ScFun {
  def isStringPlusMethod: Boolean = {
    if (name != "+") return false
    retType.extractClass(manager.getProject) match {
      case Some(clazz) => clazz.qualifiedName == "java.lang.String"
      case _ => false
    }
  }
  
  def this(manager: PsiManager, name: String, retType: ScType, paramTypes: Seq[Seq[ScType]]) =
    this(manager, name, retType, paramTypes.mapWithIndex {
      case (p, index) => p.map(new Parameter("", None, _, false, false, false, index))
    }, Seq.empty)

  val typeParams: Seq[ScSyntheticTypeParameter] =
    typeParameterNames.map {name => new ScSyntheticTypeParameter(manager, name, this)}
  override def typeParameters = typeParams

  override def getIcon(flags: Int) = icons.Icons.FUNCTION

  override def toString = "Synthetic method"

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = {
    findChildrenByClass[T](clazz)
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (clazz.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }
}

class ScSyntheticValue(manager: PsiManager, val name: String, val tp: ScType) extends SyntheticNamedElement(manager, name) {
  override def getIcon(flags: Int): Icon = icons.Icons.VAL

  override def toString = "Synthetic value"
}

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

class SyntheticClasses(project: Project) extends PsiElementFinder with ProjectComponent {
  def projectOpened() {}
  def projectClosed() {}
  def getComponentName = "SyntheticClasses"
  def disposeComponent() {}

  def initComponent() {
    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable {
      def run() {
        registerClasses()
      }
    })
  }

  private implicit val typeSystem = project.typeSystem
  private var classesInitialized: Boolean = false
  def isClassesRegistered: Boolean = classesInitialized

  def registerClasses() {
    all = new mutable.HashMap[String, ScSyntheticClass]
    file = PsiFileFactory.getInstance(project).createFileFromText(
      "dummy." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension, ScalaFileType.SCALA_FILE_TYPE, "")

    val any = registerClass(Any, "Any")
    val manager = any.manager
    any.addMethod(new ScSyntheticFunction(manager, "==", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction(manager, "!=", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction(manager, "##", Int, Seq.empty))
    any.addMethod(new ScSyntheticFunction(manager, "isInstanceOf", Boolean, Seq.empty, Seq(ScalaUtils.typeParameter)))
    any.addMethod(new ScSyntheticFunction(manager, "asInstanceOf", Any, Seq.empty, Seq(ScalaUtils.typeParameter)) {
      override val retType = ScalaPsiManager.typeVariable(typeParams.head)
    })

    val anyRef = registerClass(AnyRef, "AnyRef")
    anyRef.addMethod(new ScSyntheticFunction(manager, "eq", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction(manager, "ne", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction(manager, "synchronized", Any, Seq.empty, Seq(ScalaUtils.typeParameter)) {
      override val paramClauses: Seq[Seq[Parameter]] = Seq(Seq(new Parameter("", None,
        ScalaPsiManager.typeVariable(typeParams.head), false, false, false, 0)))
      override val retType: ScType = ScalaPsiManager.typeVariable(typeParams.head)
    })

    registerClass(AnyVal, "AnyVal")
    registerClass(Nothing, "Nothing")
    registerClass(Null, "Null")
    registerClass(Singleton, "Singleton")
    registerClass(Unit, "Unit")

    val boolc = registerClass(Boolean, "Boolean")
    for (op <- bool_bin_ops)
      boolc.addMethod(new ScSyntheticFunction(manager, op, Boolean, Seq(Seq(Boolean))))
    boolc.addMethod(new ScSyntheticFunction(manager, "unary_!", Boolean, Seq.empty))

    registerIntegerClass(registerNumericClass(registerClass(Char, "Char")))
    registerIntegerClass(registerNumericClass(registerClass(Int, "Int")))
    registerIntegerClass(registerNumericClass(registerClass(Long, "Long")))
    registerIntegerClass(registerNumericClass(registerClass(Byte, "Byte")))
    registerIntegerClass(registerNumericClass(registerClass(Short, "Short")))
    registerNumericClass(registerClass(Float, "Float"))
    registerNumericClass(registerClass(Double, "Double"))

    for (nc <- numeric) {
      for (nc1 <- numeric; op <- numeric_comp_ops)
        nc.addMethod(new ScSyntheticFunction(manager, op, Boolean, Seq(Seq(nc1.t))))
      for (nc1 <- numeric; op <- numeric_arith_ops)
        nc.addMethod(new ScSyntheticFunction(manager, op, op_type(nc, nc1), Seq(Seq(nc1.t))))
      for (nc1 <- numeric)
        nc.addMethod(new ScSyntheticFunction(manager, "to" + nc1.className, nc1.t, Seq.empty))
      for (un_op <- numeric_arith_unary_ops)
        nc.addMethod(new ScSyntheticFunction(manager, "unary_" + un_op, nc.t match {
          case Long | Double | Float => nc.t
          case _ => Int
        }, Seq.empty))
    }

    for (ic <- integer) {
      for (ic1 <- integer; op <- bitwise_bin_ops)
        ic.addMethod(new ScSyntheticFunction(manager, op, op_type(ic, ic1), Seq(Seq(ic1.t))))
      ic.addMethod(new ScSyntheticFunction(manager, "unary_~", ic.t, Seq.empty))

      val ret = ic.t match {
        case Long => Long
        case _ => Int
      }
      for (op <- bitwise_shift_ops) {
        ic.addMethod(new ScSyntheticFunction(manager, op, ret, Seq(Seq(Int))))
        ic.addMethod(new ScSyntheticFunction(manager, op, ret, Seq(Seq(Long))))
      }
    }
    scriptSyntheticValues = new mutable.HashSet[ScSyntheticValue]
    //todo: remove all scope => method value
    //todo: handle process cancelled exception
    try {
      val stringClass = ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), "java.lang.String")
      stringClass.map { stringClass =>
        scriptSyntheticValues += new ScSyntheticValue(manager, "args", JavaArrayType(ScDesignatorType(stringClass)))
      }
    }
    catch {
      case _: ProcessCanceledException =>
    }
    stringPlusMethod = new ScSyntheticFunction(manager, "+", _, Seq(Seq(Any)))

    //register synthetic objects
    syntheticObjects = new mutable.HashSet[ScObject]
    def registerObject(fileText: String) {
      val dummyFile = PsiFileFactory.getInstance(manager.getProject).
              createFileFromText("dummy." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
        ScalaFileType.SCALA_FILE_TYPE, fileText).asInstanceOf[ScalaFile]
      val obj = dummyFile.typeDefinitions.head.asInstanceOf[ScObject]
      syntheticObjects += obj
    }

    registerObject(
"""
package scala

object Boolean {
 	def box(x: Boolean): java.lang.Boolean = throw new Error()
 	def unbox(x: Object): Boolean = throw new Error()
}
"""
    )

    registerObject(
"""
package scala

object Byte {
 	def box(x: Byte): java.lang.Byte = throw new Error()
 	def unbox(x: Object): Byte = throw new Error()
  def MinValue = java.lang.Byte.MIN_VALUE
 	def MaxValue = java.lang.Byte.MAX_VALUE
}
"""
    )

    registerObject(
"""
package scala

object Char {
 	def box(x: Char): java.lang.Character = throw new Error()
 	def unbox(x: Object): Char = throw new Error()
 	def MinValue = java.lang.Character.MIN_VALUE
 	def MaxValue = java.lang.Character.MAX_VALUE
}
"""
    )

    registerObject(
"""
package scala

object Double {
 	def box(x: Double): java.lang.Double = throw new Error()
 	def unbox(x: Object): Double = throw new Error()
 	@deprecated("use Double.MinNegativeValue instead")
 	def MinValue = -java.lang.Double.MAX_VALUE
 	def MinNegativeValue = -java.lang.Double.MAX_VALUE
 	def MaxValue = java.lang.Double.MAX_VALUE
 	@deprecated("use Double.MinPositiveValue instead")
 	def Epsilon = java.lang.Double.MIN_VALUE
 	def MinPositiveValue = java.lang.Double.MIN_VALUE
 	def NaN = java.lang.Double.NaN
 	def PositiveInfinity = java.lang.Double.POSITIVE_INFINITY
 	def NegativeInfinity = java.lang.Double.NEGATIVE_INFINITY
}
"""
    )

    registerObject(
"""
package scala

object Float {
 	def box(x: Float): java.lang.Float = throw new Error()
 	def unbox(x: Object): Float = throw new Error()
 	@deprecated("use Float.MinNegativeValue instead")
 	def MinValue = -java.lang.Float.MAX_VALUE
 	def MinNegativeValue = -java.lang.Float.MAX_VALUE
 	def MaxValue = java.lang.Float.MAX_VALUE
 	@deprecated("use Float.MinPositiveValue instead")
 	def Epsilon = java.lang.Float.MIN_VALUE
 	def MinPositiveValue = java.lang.Float.MIN_VALUE
 	def NaN = java.lang.Float.NaN
 	def PositiveInfinity = java.lang.Float.POSITIVE_INFINITY
 	def NegativeInfinity = java.lang.Float.NEGATIVE_INFINITY
}
"""
    )

    registerObject(
"""
package scala

object Int {
 	def box(x: Int): java.lang.Integer = throw new Error()
 	def unbox(x: Object): Int = throw new Error()
 	def MinValue = java.lang.Integer.MIN_VALUE
 	def MaxValue = java.lang.Integer.MAX_VALUE
}
"""
    )

    registerObject(
"""
package scala

object Long {
 	def box(x: Long): java.lang.Long = throw new Error()
 	def unbox(x: Object): Long = throw new Error()
 	def MinValue = java.lang.Long.MIN_VALUE
 	def MaxValue = java.lang.Long.MAX_VALUE
}
"""
    )

    registerObject(
"""
package scala

object Short {
 	def box(x: Short): java.lang.Short = throw new Error()
 	def unbox(x: Object): Short = throw new Error()
 	def MinValue = java.lang.Short.MIN_VALUE
 	def MaxValue = java.lang.Short.MAX_VALUE
}
"""
    )

    registerObject(
"""
package scala

object Unit
"""
    )

    classesInitialized = true
  }

  var stringPlusMethod: ScType => ScSyntheticFunction = null
  var scriptSyntheticValues: mutable.Set[ScSyntheticValue] = new mutable.HashSet[ScSyntheticValue]
  var all: mutable.Map[String, ScSyntheticClass] = new mutable.HashMap[String, ScSyntheticClass]
  var numeric: mutable.Set[ScSyntheticClass] = new mutable.HashSet[ScSyntheticClass]
  var integer : mutable.Set[ScSyntheticClass] = new mutable.HashSet[ScSyntheticClass]
  var syntheticObjects: mutable.Set[ScObject] = new mutable.HashSet[ScObject]

  def op_type (ic1 : ScSyntheticClass, ic2 : ScSyntheticClass) = (ic1.t, ic2.t) match {
    case (_, Double) | (Double, _) => Double
    case (Float, _) | (_, Float) => Float
    case (_, Long) | (Long, _)=> Long
    case _ => Int
  }

  var file : PsiFile = _

  def registerClass(t: StdType, name: String) = {
    val manager = PsiManager.getInstance(project)
    val clazz = new ScSyntheticClass(manager, name, t) {
      override def getQualifiedName = "scala." + name
    }

    all += ((name, clazz)); clazz
  }

  def registerIntegerClass(clazz : ScSyntheticClass) = {integer += clazz; clazz}
  def registerNumericClass(clazz : ScSyntheticClass) = {numeric += clazz; clazz}


  def getAll: Iterable[ScSyntheticClass] = all.values

  def byName(name: String) = all.get(name)

  val numeric_comp_ops = "==" :: "!=" :: "<" :: ">" :: "<=" :: ">=" :: Nil
  val numeric_arith_ops = "+" :: "-" :: "*" :: "/" :: "%" :: Nil
  val numeric_arith_unary_ops = "+" :: "-" :: Nil
  val bool_bin_ops = "&&" :: "||" :: "&" :: "|" :: "==" :: "!=" :: "^" :: Nil
  val bitwise_bin_ops = "&" :: "|" :: "^" :: Nil
  val bitwise_shift_ops = "<<" :: ">>" :: ">>>" :: Nil

  val prefix = "scala."
  def findClass(qName: String, scope: GlobalSearchScope): PsiClass = {
    if (qName.startsWith(prefix)) {
      byName(qName.substring(prefix.length)) match {
        case Some(c) => return c
        case _ =>
      }
    }
    for (obj <- syntheticObjects) {
      if (obj.qualifiedName == qName) return obj
    }
    null
  }

  def findClasses(qName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    val res: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]
    val c = findClass(qName, scope)
    if (c != null) res += c
    for (obj <- syntheticObjects) {
      if (obj.qualifiedName == qName) res += obj
    }
    res.toArray
  }

  override def getClasses(p : PsiPackage, scope : GlobalSearchScope) = findClasses(p.getQualifiedName, scope)

  def getScriptSyntheticValues: Seq[ScSyntheticValue] = scriptSyntheticValues.toSeq
}

object SyntheticClasses {
  def get(project: Project): SyntheticClasses = project.getComponent(classOf[SyntheticClasses])
}

