package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.{NlsString, ScalaFileType, ScalaLanguage}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitProcessor
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}

import javax.swing.Icon
import scala.collection.mutable

abstract sealed class SyntheticNamedElement(name: String)
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

  override def getNameIdentifier: PsiIdentifier = null
}

final class ScSyntheticTypeParameter(override val name: String, override val owner: ScFun)
  extends SyntheticNamedElement(name)(owner.projectContext) with ScTypeParam with PsiClassFake {

  override def nameId: PsiElement = null

  override def typeParameterText: String = name

  override def getPresentation: ItemPresentation = super[ScTypeParam].getPresentation

  override def getContainingFileName: String = "NoFile"

  override def toString: String = "Synthetic type parameter: " + name

  override def isCovariant = false
  override def isContravariant = false

  override def lowerBound: Right[Nothing, StdType] = Right(Nothing)

  override def upperBound: TypeResult = Right(Any)

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

      override def getPresentableText: String = NlsString.force(This.className)

      override def getIcon(open: Boolean): Icon = This.getIcon(0)
    }
  }

  override def getNameIdentifier: PsiIdentifier = null

  override def toString = "Synthetic class"

  val syntheticMethods = new MultiMap[String, ScSyntheticFunction]()

  def addMethod(method: ScSyntheticFunction): Unit = syntheticMethods.putValue(method.name, method)

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    processor match {
      case p: ResolveProcessor =>
        val name = ScalaNamesUtil.clean(state.renamed.getOrElse(p.name))
        syntheticMethods.get(name).forEach { method =>
          if (!processor.execute(method, state)) return false
        }
      case _: ImplicitProcessor => //do nothing, there is no implicit synthetic methods
      case _: BaseProcessor =>
        //method toString and hashCode exists in java.lang.Object
        syntheticMethods.values().forEach { method =>
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

sealed class ScSyntheticFunction(
  val name: String,
  override val retType: ScType,
  override val paramClauses: Seq[Seq[Parameter]],
  typeParameterNames: Seq[String]
)(implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(name) with ScFun {
  def isStringPlusMethod: Boolean = {
    if (name != "+") return false
    retType.extractClass match {
      case Some(clazz) => clazz.qualifiedName == "java.lang.String"
      case _ => false
    }
  }

  def this(name: String, retType: ScType, paramTypes: Seq[Seq[ScType]], paramsByName: Boolean = false)
          (implicit ctx: ProjectContext) =
    this(name, retType, paramTypes.mapWithIndex {
      case (p, index) => p.map(Parameter(_, isRepeated = false, isByName = paramsByName, index = index))
    }, Nil)

  val typeParams: Seq[ScSyntheticTypeParameter] =
    typeParameterNames.map { name => new ScSyntheticTypeParameter(name, this) }
  override def typeParameters: Seq[ScTypeParam] = typeParams

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

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

final class ScSyntheticValue(val name: String, val tp: ScType)
                            (implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(name) {
  override def getIcon(flags: Int): Icon = Icons.VAL

  override def toString = "Synthetic value"
}

import com.intellij.openapi.project.Project

final class SyntheticClasses(project: Project) {
  implicit def ctx: ProjectContext = project

  private[synthetic] def clear(): Unit = {
    if (classesInitialized) {
      sharedClasses.clear()
      scala3Classes.clear()
      numeric.clear()
      integer.clear()
      objects.clear()
      aliases.clear()
    }

    stringPlusMethod = null
    numeric = null
    integer = null
    file = null
  }

  @volatile
  private var classesInitialized: Boolean = false

  def isClassesRegistered: Boolean = classesInitialized

  var stringPlusMethod: ScType => ScSyntheticFunction = _

  val sharedClasses: mutable.Map[String, PsiClass] = mutable.HashMap.empty[String, PsiClass]
  val scala3Classes: mutable.Map[String, PsiClass] = mutable.HashMap.empty[String, PsiClass]
  var numeric: mutable.Set[ScSyntheticClass]       = mutable.HashSet.empty[ScSyntheticClass]
  var integer: mutable.Set[ScSyntheticClass]       = mutable.HashSet.empty[ScSyntheticClass]
  val objects: mutable.Map[String, ScObject]       = mutable.HashMap.empty[String, ScObject]
  val aliases: mutable.Set[ScTypeAlias]            = mutable.HashSet.empty[ScTypeAlias]

  private[synthetic]
  var file : PsiFile = _

  def registerClasses(): Unit = {
    val stdTypes = ctx.stdTypes
    import stdTypes._
    val typeParameters = SyntheticClasses.TypeParameter :: Nil

    val fileName = s"dummy-synthetics.scala"
    val emptyScalaFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, ScalaFileType.INSTANCE, "")
    file = emptyScalaFile

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
    for (op <- bool_other_bin_ops)
      boolc.addMethod(new ScSyntheticFunction(op, Boolean, Seq(Seq(Boolean))))
    boolc.addMethod(new ScSyntheticFunction("unary_!", Boolean, Nil))

    for (op <- bool_bin_short_circuit_ops)
      boolc.addMethod(new ScSyntheticFunction(op, Boolean, Seq(Seq(Boolean)), paramsByName = true))

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

    def createDummyFile(debugName: String, fileText: String) = {
      val fileName = s"dummy-synthetic-$debugName.scala"
      PsiFileFactory
        .getInstance(project)
        .createFileFromText(fileName, ScalaFileType.INSTANCE, fileText)
        .asInstanceOf[ScalaFile]
    }

    //register synthetic objects
    def registerObject(debugName: String, fileText: String): Unit = {
      val dummyFile = createDummyFile(debugName, fileText)
      val obj = dummyFile.typeDefinitions.head.asInstanceOf[ScObject]
      objects.put(obj.qualifiedName, obj)
    }

    def registerContextFunctionClass(debugName: String, fileText: String): Unit = {
      val dummyFile = createDummyFile(debugName, fileText)
      val cls = dummyFile.typeDefinitions.head.asInstanceOf[PsiClass]
      scala3Classes.put(cls.name, cls)
    }

    (1 to 22).foreach { n =>
      val typeParameters    = (1 to n).map(i => s"-T$i").mkString(", ")
      val contextParameters = (1 to n).map(i => s"x$i: T$i").mkString(", ")

      registerContextFunctionClass("ContextFunction",
       s"""
           |package scala
           |
           |trait ContextFunction$n[$typeParameters, +R] {
           |  def apply(implicit $contextParameters): R
           |}
           |""".stripMargin
      )
    }

    registerObject("Boolean",
"""
package scala

object Boolean {
 	def box(x: _root_.scala.Boolean): _root_.java.lang.Boolean = ???

 	def unbox(x: _root_.java.lang.Object): _root_.scala.Boolean = ???
}
"""
    )

    registerObject("Byte",
"""
package scala

object Byte {
 	def box(x: _root_.scala.Byte): _root_.java.lang.Byte = ???

 	def unbox(x: _root_.java.lang.Object): _root_.scala.Byte = ???

  final val MinValue = _root_.java.lang.Byte.MIN_VALUE

 	final val MaxValue = _root_.java.lang.Byte.MAX_VALUE
}
"""
    )

    registerObject("Char",
"""
package scala

object Char {
 	def box(x: _root_.scala.Char): _root_.java.lang.Character = ???
  
 	def unbox(x: _root_.java.lang.Object): _root_.scala.Char = ???
  
 	final val MinValue = _root_.java.lang.Character.MIN_VALUE
  
 	final val MaxValue = _root_.java.lang.Character.MAX_VALUE
}
"""
    )

    registerObject("Double",
"""
package scala

object Double {
 	def box(x: _root_.scala.Double): _root_.java.lang.Double = ???
  
 	def unbox(x: _root_.java.lang.Object): _root_.scala.Double = ???
  
 	@deprecated("use Double.MinNegativeValue instead")
 	final val MinValue = -_root_.java.lang.Double.MAX_VALUE
  
 	final val MinNegativeValue = -_root_.java.lang.Double.MAX_VALUE
  
 	final val MaxValue = _root_.java.lang.Double.MAX_VALUE
  
 	@deprecated("use Double.MinPositiveValue instead")
 	final val Epsilon = _root_.java.lang.Double.MIN_VALUE
  
 	final val MinPositiveValue = _root_.java.lang.Double.MIN_VALUE
  
 	final val NaN = _root_.java.lang.Double.NaN
  
 	final val PositiveInfinity = _root_.java.lang.Double.POSITIVE_INFINITY
  
 	final val NegativeInfinity = _root_.java.lang.Double.NEGATIVE_INFINITY
}
"""
    )

    registerObject("Float",
"""
package scala

object Float {
 	def box(x: _root_.scala.Float): _root_.java.lang.Float = ???
  
 	def unbox(x: _root_.java.lang.Object): _root_.scala.Float = ???
  
 	@deprecated("use Float.MinNegativeValue instead")
 	final val MinValue = -_root_.java.lang.Float.MAX_VALUE
 	
  final val MinNegativeValue = -_root_.java.lang.Float.MAX_VALUE
 	
  final val MaxValue = _root_.java.lang.Float.MAX_VALUE
 	
  @deprecated("use Float.MinPositiveValue instead")
 	final val Epsilon = _root_.java.lang.Float.MIN_VALUE
 	
  final val MinPositiveValue = _root_.java.lang.Float.MIN_VALUE
 	
  final val NaN = _root_.java.lang.Float.NaN
 	
  final val PositiveInfinity = _root_.java.lang.Float.POSITIVE_INFINITY
 	
  final val NegativeInfinity = _root_.java.lang.Float.NEGATIVE_INFINITY
}
"""
    )

    registerObject("Int",
"""
package scala

object Int {
 	def box(x: _root_.scala.Int): _root_.java.lang.Integer = ???
 	
  def unbox(x: _root_.java.lang.Object): _root_.scala.Int = ???
 	
  final val MinValue = _root_.java.lang.Integer.MIN_VALUE
 	
  final val MaxValue = _root_.java.lang.Integer.MAX_VALUE
}
"""
    )

    registerObject("Long",
"""
package scala

object Long {
 	def box(x: _root_.scala.Long): _root_.java.lang.Long = ???
 	
  def unbox(x: _root_.java.lang.Object): _root_.scala.Long = ???
 	
  final val MinValue = _root_.java.lang.Long.MIN_VALUE
 	
  final val MaxValue = _root_.java.lang.Long.MAX_VALUE
}
"""
    )

    registerObject("Short",
"""
package scala

object Short {
 	def box(x: _root_.scala.Short): _root_.java.lang.Short = ???
 	
  def unbox(x: _root_.java.lang.Object): _root_.scala.Short = ???
 	
  final val MinValue = _root_.java.lang.Short.MIN_VALUE
 	
  final val MaxValue = _root_.java.lang.Short.MAX_VALUE
}
"""
    )

    registerObject("Unit",
"""
package scala

object Unit
"""
    )

    def registerAlias(text: String): Unit = {
      val file  = ScalaPsiElementFactory.createScalaFileFromText(text, ScalaFeatures.default)
      val alias = file.members.head.asInstanceOf[ScTypeAlias]
      aliases += alias
    }

    registerAlias(
      """
        |package scala
        |
        |type &[A, B]
        |""".stripMargin
    )

    registerAlias(
      """
        |package scala
        |
        |type |[A, B]
        |""".stripMargin
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

    sharedClasses += ((name, clazz)); clazz
  }

  def registerIntegerClass(clazz : ScSyntheticClass): ScSyntheticClass = {integer += clazz; clazz}
  def registerNumericClass(clazz : ScSyntheticClass): ScSyntheticClass = {numeric += clazz; clazz}

  def getAll: Iterable[PsiClass] = sharedClasses.values ++ scala3Classes.values

  def sharedClassesOnly: Iterable[PsiClass] = sharedClasses.values

  def byName(name: String): Option[PsiClass] = sharedClasses.get(name).orElse(scala3Classes.get(name))

  val prefix = "scala."

  def findClass(qName: String): PsiClass = {
    if (qName.startsWith(prefix)) {
      byName(qName.substring(prefix.length)) match {
        case Some(c) => return c
        case _ =>
      }
    }
    objects.get(qName).orNull
  }

  def findClasses(qName: String): Array[PsiClass] = {
    val c = findClass(qName)
    val obj = objects.get(qName).orNull

    if (c != null && obj != null && c != obj)
      Array(c, obj)
    else if (c != null)
      Array(c)
    else
      Array.empty
  }
}

final class SyntheticClassElementFinder(project: Project) extends PsiElementFinder {
  private[this] val instance = SyntheticClasses.get(project)

  override def findClass(
    qualifiedName: String,
    scope:         GlobalSearchScope
  ): PsiClass = instance.findClass(qualifiedName)

  override def findClasses(
    qualifiedName: String,
    scope:         GlobalSearchScope
  ): Array[PsiClass] = instance.findClasses(qualifiedName)
}

object SyntheticClasses {
  def get(project: Project): SyntheticClasses = project.getService(classOf[SyntheticClasses])

  val TypeParameter = "TypeParameterForSyntheticFunction"

  val numeric_comp_ops: List[String] = "==" :: "!=" :: "<" :: ">" :: "<=" :: ">=" :: Nil
  val numeric_arith_ops: List[String] = "+" :: "-" :: "*" :: "/" :: "%" :: Nil
  val numeric_arith_unary_ops: List[String] = "+" :: "-" :: Nil
  val bool_bin_short_circuit_ops: List[String] = "&&" :: "||" :: Nil
  val bool_other_bin_ops: List[String] = "&" :: "|" :: "==" :: "!=" :: "^" :: Nil
  val bool_bin_ops: List[String] = bool_bin_short_circuit_ops ++ bool_other_bin_ops
  val bitwise_bin_ops: List[String] = "&" :: "|" :: "^" :: Nil
  val bitwise_shift_ops: List[String] = "<<" :: ">>" :: ">>>" :: Nil

}

final class RegisterSyntheticClassesStartupActivity extends StartupActivity.DumbAware {
  override def runActivity(project: Project): Unit = {
    // NOTE: run `registerClasses` on EDT!
    // Don't use `inReadAction`, it can significantly increase setup time for each test case.
    // Details: DumbAware startup activity is run on the background thread.
    // Under the hood `registerClasses` involves parsing of registered synthetic classes.
    // This parsing can take a long time, if it's done on a background thread.
    // This is because, during the parsing `ProgressManager.checkCanceled()` is being frequently called.
    // And it calls `CoreProgressManager.sleepIfNeededToGivePriorityToAnotherThread`.
    // This method causes the parsing thread to sleep 1s each time there is some other thread with a higher priority.
    // This can increase light test project initialization from 0.1s to 12s on Windows (!)
    invokeLater(SyntheticClasses.get(project).registerClasses())
  }
}

class DeregisterSyntheticClassesListener extends ProjectManagerListener {
  override def projectClosing(project: Project): Unit = {
    SyntheticClasses.get(project).clear()
  }
}

