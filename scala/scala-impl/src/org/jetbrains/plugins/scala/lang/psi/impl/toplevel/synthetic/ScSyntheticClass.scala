package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupManager
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFun
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.{Seq, mutable}

abstract class SyntheticNamedElement(name: String)
                                    (implicit projectContext: ProjectContext)
  extends LightElement(projectContext, ScalaLanguage.INSTANCE) with PsiNameIdentifierOwner {
  override def getName: String = name
  override def getText = ""
  override def setName(newName: String) : PsiElement = throw new IncorrectOperationException("nonphysical element")
  override def copy = throw new IncorrectOperationException("nonphysical element")
  override def accept(v: PsiElementVisitor): Unit = {
    throw new IncorrectOperationException("should not call")
  }
  override def getContainingFile: PsiFile = SyntheticClasses.get(projectContext).file

  def nameId: PsiElement = null
  override def getNameIdentifier: PsiIdentifier = null
}

class ScSyntheticTypeParameter(override val name: String, override val owner: ScFun)
  extends SyntheticNamedElement(name)(owner.projectContext) with ScTypeParam with PsiClassFake {

  override def typeParameterText: String = name

  override def getPresentation: ItemPresentation = super[ScTypeParam].getPresentation

  def getOffsetInFile: Int = 0

  override def getContainingFileName: String = "NoFile"

  override def toString: String = "Synthetic type parameter: " + name

  override def isCovariant = false
  override def isContravariant = false

  override def lowerBound = Right(Nothing)

  override def upperBound = Right(Any)

  override def getIndex: Int = -1
  override def getOwner: PsiTypeParameterListOwner = null

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def isHigherKindedTypeParameter: Boolean = false

  override val typeParamId: Long = -1
}
// we could try and implement all type system related stuff
// with class types, but it is simpler to indicate types corresponding to synthetic classes explicitly
sealed class ScSyntheticClass(val className: String, val stdType: StdType)
                             (implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(className) with PsiClassAdapter with PsiClassFake {
  override def getPresentation: ItemPresentation = {
    new ItemPresentation {
      val This: ScSyntheticClass = ScSyntheticClass.this
      override def getLocationString: String = "(scala)"

      def getTextAttributesKey: TextAttributesKey = null

      override def getPresentableText: String = This.className

      override def getIcon(open: Boolean): Icon = This.getIcon(0)
    }
  }

  override def getNameIdentifier: PsiIdentifier = null

  override def toString = "Synthetic class"

  def syntheticMethods(scope: GlobalSearchScope): List[ScSyntheticFunction] = methods.values.flatten.toList ++
          specialMethods.values.flatMap(s => s.map(_(scope))).toList

  protected object methods extends mutable.HashMap[String, mutable.Set[ScSyntheticFunction]] with mutable.MultiMap[String, ScSyntheticFunction]
  protected object specialMethods extends mutable.HashMap[String, mutable.Set[GlobalSearchScope => ScSyntheticFunction]] with
          mutable.MultiMap[String, GlobalSearchScope => ScSyntheticFunction]

  def addMethod(method: ScSyntheticFunction): methods.type = methods.addBinding(method.name, method)
  def addMethod(method: GlobalSearchScope => ScSyntheticFunction, methodName: String): specialMethods.type = specialMethods.addBinding(methodName, method)

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    processor match {
      case p: ResolveProcessor =>
        val name = ScalaNamesUtil.clean(state.renamed.getOrElse(p.name))
        methods.get(name) match {
          case Some(ms) => for (method <- ms) {
            if (!processor.execute(method, state)) return false
          }
          case None =>
        }
      case _: implicits.ImplicitProcessor => //do nothing, there is no implicit synthetic methods
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
    stdType.tSuper match {
      case None => PsiClassType.EMPTY_ARRAY
      case Some(ts) =>
        val syntheticClass = ts.syntheticClass.getOrElse(return PsiClassType.EMPTY_ARRAY)
        val factory = JavaPsiFacade.getInstance(projectContext).getElementFactory
        Array[PsiClassType](factory.createType(syntheticClass, PsiSubstitutor.EMPTY))
    }
  }
}

class ScSyntheticFunction(val name: String, override val retType: ScType, override val paramClauses: Seq[Seq[Parameter]], typeParameterNames: Seq[String])
                         (implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(name) with ScFun {
  def isStringPlusMethod: Boolean = {
    if (name != "+") return false
    retType.extractClass match {
      case Some(clazz) => clazz.qualifiedName == "java.lang.String"
      case _ => false
    }
  }

  def this(name: String, retType: ScType, paramTypes: Seq[Seq[ScType]])
          (implicit ctx: ProjectContext) =
    this(name, retType, paramTypes.mapWithIndex {
      case (p, index) => p.map(Parameter(_, isRepeated = false, index = index))
    }, Nil)

  val typeParams: Seq[ScSyntheticTypeParameter] =
    typeParameterNames.map { name => new ScSyntheticTypeParameter(name, this) }
  override def typeParameters: Seq[ScTypeParam] = typeParams

  override def getIcon(flags: Int): Icon = icons.Icons.FUNCTION

  override def toString = "Synthetic method"

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = {
    findChildrenByClass[T](clazz)
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (clazz.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }
}

class ScSyntheticValue(val name: String, val tp: ScType)
                      (implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(name) {
  override def getIcon(flags: Int): Icon = icons.Icons.VAL

  override def toString = "Synthetic value"
}

import com.intellij.openapi.project.Project

class SyntheticClasses(project: Project) extends PsiElementFinder {
  implicit def ctx: ProjectContext = project

  private[synthetic] def clear(): Unit = {
    if (classesInitialized) {
      all.clear()
      numeric.clear()
      integer.clear()
      syntheticObjects.clear()
    }

    stringPlusMethod = null
    all = null
    numeric = null
    integer = null
    file = null
  }

  @volatile
  private var classesInitialized: Boolean = false

  def isClassesRegistered: Boolean = classesInitialized

  var stringPlusMethod: ScType => ScSyntheticFunction = _

  var all             : mutable.Map[String, ScSyntheticClass] = new mutable.HashMap[String, ScSyntheticClass]
  var numeric         : mutable.Set[ScSyntheticClass]         = new mutable.HashSet[ScSyntheticClass]
  var integer         : mutable.Set[ScSyntheticClass]         = new mutable.HashSet[ScSyntheticClass]
  val syntheticObjects: mutable.Map[String, ScObject]         = new mutable.HashMap[String, ScObject]

  var file : PsiFile = _

  def registerClasses(): Unit = {
    val stdTypes = ctx.stdTypes
    import stdTypes._
    val typeParameters = SyntheticClasses.TypeParameter :: Nil

    all = new mutable.HashMap[String, ScSyntheticClass]
    file = PsiFileFactory.getInstance(project).createFileFromText(
      "dummy." + ScalaFileType.INSTANCE.getDefaultExtension, ScalaFileType.INSTANCE, "")

    val any = registerClass(Any, "Any")
    any.addMethod(new ScSyntheticFunction("==", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction("!=", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction("##", Int, Nil))
    any.addMethod(new ScSyntheticFunction("isInstanceOf", Boolean, Nil, typeParameters))
    any.addMethod(new ScSyntheticFunction("asInstanceOf", Any, Nil, typeParameters) {
      override val retType: ScType = TypeParameterType(typeParams.head)
    })

    val anyRef = registerClass(AnyRef, "AnyRef")
    anyRef.addMethod(new ScSyntheticFunction("eq", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction("ne", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction("synchronized", Any, Nil, typeParameters) {
      override val paramClauses: Seq[Seq[Parameter]] = Seq(Seq(Parameter(
        TypeParameterType(typeParams.head), isRepeated = false, index = 0)))
      override val retType: ScType = TypeParameterType(typeParams.head)
    })

    registerClass(AnyVal, "AnyVal")
    registerClass(Nothing, "Nothing")
    registerClass(Null, "Null")
    registerClass(Singleton, "Singleton")
    registerClass(Unit, "Unit")

    import SyntheticClasses._

    val boolc = registerClass(Boolean, "Boolean")
    for (op <- bool_bin_ops)
      boolc.addMethod(new ScSyntheticFunction(op, Boolean, Seq(Seq(Boolean))))
    boolc.addMethod(new ScSyntheticFunction("unary_!", Boolean, Nil))

    registerIntegerClass(registerNumericClass(registerClass(Char, "Char")))
    registerIntegerClass(registerNumericClass(registerClass(Int, "Int")))
    registerIntegerClass(registerNumericClass(registerClass(Long, "Long")))
    registerIntegerClass(registerNumericClass(registerClass(Byte, "Byte")))
    registerIntegerClass(registerNumericClass(registerClass(Short, "Short")))
    registerNumericClass(registerClass(Float, "Float"))
    registerNumericClass(registerClass(Double, "Double"))

    for (nc <- numeric) {
      for (nc1 <- numeric; op <- numeric_comp_ops)
        nc.addMethod(new ScSyntheticFunction(op, Boolean, Seq(Seq(nc1.stdType))))
      for (nc1 <- numeric; op <- numeric_arith_ops)
        nc.addMethod(new ScSyntheticFunction(op, op_type(nc, nc1), Seq(Seq(nc1.stdType))))
      for (nc1 <- numeric)
        nc.addMethod(new ScSyntheticFunction("to" + nc1.className, nc1.stdType, Nil))
      for (un_op <- numeric_arith_unary_ops)
        nc.addMethod(new ScSyntheticFunction("unary_" + un_op, nc.stdType match {
          case Long | Double | Float => nc.stdType
          case _ => Int
        }, Nil))
    }

    for (ic <- integer) {
      for (ic1 <- integer; op <- bitwise_bin_ops)
        ic.addMethod(new ScSyntheticFunction(op, op_type(ic, ic1), Seq(Seq(ic1.stdType))))
      ic.addMethod(new ScSyntheticFunction("unary_~", ic.stdType, Nil))

      val ret = ic.stdType match {
        case Long => Long
        case _ => Int
      }
      for (op <- bitwise_shift_ops) {
        ic.addMethod(new ScSyntheticFunction(op, ret, Seq(Seq(Int))))
        ic.addMethod(new ScSyntheticFunction(op, ret, Seq(Seq(Long))))
      }
    }
    stringPlusMethod = new ScSyntheticFunction("+", _, Seq(Seq(Any)))

    //register synthetic objects
    def registerObject(fileText: String): Unit = {
      val dummyFile = PsiFileFactory.getInstance(project).
        createFileFromText("dummy." + ScalaFileType.INSTANCE.getDefaultExtension,
          ScalaFileType.INSTANCE, fileText).asInstanceOf[ScalaFile]
      val obj = dummyFile.typeDefinitions.head.asInstanceOf[ScObject]
      syntheticObjects.put(obj.qualifiedName, obj)
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

  def op_type (ic1 : ScSyntheticClass, ic2 : ScSyntheticClass): ValType = {
    op_type(ic1.stdType, ic2.stdType)
  }

  def op_type(ic1: StdType, ic2: StdType): ValType = {
    val stdTypes = ic1.projectContext.stdTypes
    import stdTypes._
    (ic1, ic2) match {
      case (_, Double) | (Double, _) => Double
      case (Float, _) | (_, Float) => Float
      case (_, Long) | (Long, _)=> Long
      case _ => Int
    }
  }

  def registerClass(t: StdType, name: String): ScSyntheticClass = {
    val clazz = new ScSyntheticClass(name, t) {
      override def getQualifiedName: String = "scala." + name
    }

    all += ((name, clazz)); clazz
  }

  def registerIntegerClass(clazz : ScSyntheticClass): ScSyntheticClass = {integer += clazz; clazz}
  def registerNumericClass(clazz : ScSyntheticClass): ScSyntheticClass = {numeric += clazz; clazz}


  def getAll: Iterable[ScSyntheticClass] = all.values

  def byName(name: String): Option[ScSyntheticClass] = all.get(name)

  val prefix = "scala."
  override def findClass(qName: String, scope: GlobalSearchScope): PsiClass = {
    if (qName.startsWith(prefix)) {
      byName(qName.substring(prefix.length)) match {
        case Some(c) => return c
        case _ =>
      }
    }
    syntheticObjects.get(qName).orNull
  }

  override def findClasses(qName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    val c = findClass(qName, scope)
    val obj = syntheticObjects.get(qName).orNull

    if (c != null && obj != null && c != obj)
      Array(c, obj)
    else if (c != null)
      Array(c)
    else
      Array.empty
  }

  override def getClasses(p : PsiPackage, scope : GlobalSearchScope): Array[PsiClass] = findClasses(p.getQualifiedName, scope)
}

object SyntheticClasses {
  def get(project: Project): SyntheticClasses = project.getService(classOf[SyntheticClasses])

  val TypeParameter = "TypeParameterForSyntheticFunction"

  val numeric_comp_ops: List[String] = "==" :: "!=" :: "<" :: ">" :: "<=" :: ">=" :: Nil
  val numeric_arith_ops: List[String] = "+" :: "-" :: "*" :: "/" :: "%" :: Nil
  val numeric_arith_unary_ops: List[String] = "+" :: "-" :: Nil
  val bool_bin_ops: List[String] = "&&" :: "||" :: "&" :: "|" :: "==" :: "!=" :: "^" :: Nil
  val bitwise_bin_ops: List[String] = "&" :: "|" :: "^" :: Nil
  val bitwise_shift_ops: List[String] = "<<" :: ">>" :: ">>>" :: Nil

}

class SyntheticClassesListener extends ProjectManagerListener {
  override def projectOpened(project: Project): Unit = {
    StartupManager.getInstance(project).registerPostStartupActivity { () =>
      SyntheticClasses.get(project).registerClasses()
    }
  }

  override def projectClosing(project: Project): Unit = {
    SyntheticClasses.get(project).clear()
  }
}

