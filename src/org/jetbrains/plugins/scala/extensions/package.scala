package org.jetbrains.plugins.scala

import java.io.Closeable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{Callable, Future}
import javax.swing.SwingUtilities

import com.intellij.openapi.application.{ApplicationManager, Result}
import com.intellij.openapi.command.{CommandProcessor, WriteCommandAction}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, ThrowableComputable}
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.implementation._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.generic.CanBuildFrom
import scala.io.Source
import scala.language.higherKinds
import scala.reflect.{ClassTag, classTag}
import scala.runtime.NonLocalReturnControl
import scala.util.control.Exception.catching
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * Pavel Fatin
  */

package object extensions {
  implicit class PsiMethodExt(val repr: PsiMethod) extends AnyVal {
    import org.jetbrains.plugins.scala.extensions.PsiMethodExt._

    def isAccessor: Boolean = {
      hasNoParams && hasQueryLikeName && !hasVoidReturnType
    }

    def isMutator: Boolean = {
      hasVoidReturnType || hasMutatorLikeName
    }

    def hasQueryLikeName = {
      def startsWith(name: String, prefix: String) =
        name.length > prefix.length && name.startsWith(prefix) && name.charAt(prefix.length).isUpper

      repr.getName match {
        case "getInstance" => false // TODO others?
        case name if startsWith(name, "getAnd") || startsWith(name, "getOr") => false
        case AccessorNamePattern() => true
        case _ => false
      }
    }

    def hasMutatorLikeName = repr.getName match {
      case MutatorNamePattern() => true
      case _ => false
    }

    def hasVoidReturnType = repr.getReturnType == PsiType.VOID

    def hasNoParams = repr.getParameterList.getParameters.isEmpty
  }

  object PsiMethodExt {
    val AccessorNamePattern =
      """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*""".r

    val MutatorNamePattern =
      """(?-i)(?:do|set|add|remove|insert|delete|aquire|release|update)(?:\p{Lu}.*)""".r
  }

  implicit class TraversableExt[CC[X] <: Traversable[X], A](val value: CC[A]) extends AnyVal {
    private type CanBuildTo[Elem, C[X]] = CanBuildFrom[Nothing, Elem, C[Elem]]

    def filterBy[T](aClass: Class[T])(implicit cbf: CanBuildTo[T, CC]): CC[T] =
      value.filter(aClass.isInstance(_)).map[T, CC[T]](_.asInstanceOf[T])(collection.breakOut)

    def findBy[T](aClass: Class[T]): Option[T] =
      value.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

    def mkParenString(implicit ev: A <:< String): String = value.mkString("(", ", ", ")")
  }

  implicit class SeqExt[CC[X] <: Seq[X], A](val value: CC[A]) extends AnyVal {
    private type CanBuildTo[Elem, C[X]] = CanBuildFrom[Nothing, Elem, C[Elem]]

    def distinctBy[K](f: A => K)(implicit cbf: CanBuildTo[A, CC]): CC[A] = {
      val b = cbf()
      var seen = Set[K]()
      for (x <- value) {
        val v = f(x)
        if (!(seen contains v)) {
          b += x
          seen = seen + v
        }
      }
      b.result()
    }

    def mapWithIndex[B](f: (A, Int) => B)(implicit cbf: CanBuildTo[B, CC]): CC[B] = {
      val b = cbf()
      var i = 0
      for (x <- value) {
        b += f(x, i)
        i += 1
      }
      b.result()
    }

    def foreachWithIndex[B](f: (A, Int) => B) {
      var i = 0
      for (x <- value) {
        f(x, i)
        i += 1
      }
    }
  }

  implicit class IterableExt[CC[X] <: Iterable[X], A](val value: CC[A]) extends AnyVal {
    private type CanBuildTo[Elem, C[X]] = CanBuildFrom[Nothing, Elem, C[Elem]]

    def zipMapped[B](f: A => B)(implicit cbf: CanBuildTo[(A, B), CC]): CC[(A, B)] = {
      val b = cbf()
      val it = value.iterator
      while (it.hasNext) {
        val v = it.next()
        b += ((v, f(v)))
      }
      b.result()
    }
  }

  implicit class ObjectExt[T](val v: T) extends AnyVal{
    def toOption: Option[T] = Option(v)

    def asOptionOf[E: ClassTag]: Option[E] = {
      if (classTag[E].runtimeClass.isInstance(v)) Some(v.asInstanceOf[E])
      else None
    }

    def getOrElse[H >: T](default: H): H = if (v == null) default else v

    def collectOption[B](pf : scala.PartialFunction[T, B]) = Some(v).collect(pf)
  }

  implicit class BooleanExt(val b: Boolean) extends AnyVal {
    def ifTrue[T](value: => T) = if (b) Some(value) else None

    def toInt: Int = if (b) 1 else 0
  }

  implicit class StringExt(val s: String) extends AnyVal{
    def startsWith(c: Char) = !s.isEmpty && s.charAt(0) == c

    def endsWith(c: Char) = !s.isEmpty && s.charAt(s.length - 1) == c

    def parenthesisedIf(condition: Boolean) = if (condition) "(" + s + ")" else s
  }

  implicit class PsiElementExt(override val repr: PsiElement) extends PsiElementExtTrait {
    def startOffsetInParent: Int = {
      repr match {
        case s: ScalaPsiElement => s.startOffsetInParent
        case _ => repr.getStartOffsetInParent
      }
    }

    def typeSystem = repr.getProject.typeSystem
  }

  implicit class PsiMemberExt(val member: PsiMember) extends AnyVal {
    /**
     * Second match branch is for Java only.
     */
    def containingClass: PsiClass = {
      member match {
        case member: ScMember => member.containingClass
        case b: ScBindingPattern => b.containingClass
        case _ => member.getContainingClass
      }
    }
  }

  implicit class PsiClassExt(val clazz: PsiClass) extends AnyVal {
    /**
     * Second match branch is for Java only.
     */
    def qualifiedName: String = {
      clazz match {
        case t: ScTemplateDefinition => t.qualifiedName
        case _ => clazz.getQualifiedName
      }
    }

    def constructors: Array[PsiMethod] = {
      clazz match {
        case c: ScClass => c.constructors
        case _ => clazz.getConstructors
      }
    }

    def isEffectivelyFinal: Boolean = clazz match {
      case scClass: ScClass => scClass.hasFinalModifier
      case _: ScObject => true
      case synth: ScSyntheticClass if !Seq("AnyRef", "AnyVal").contains(synth.className) => true //wrappers for value types
      case _ => clazz.hasModifierProperty(PsiModifier.FINAL)
    }


    def processPsiMethodsForNode(node: SignatureNodes.Node, isStatic: Boolean, isInterface: Boolean)
                                (processMethod: PsiMethod => Unit, processName: String => Unit = _ => ()): Unit = {

      def concreteClassFor(typedDef: ScTypedDefinition): Option[PsiClass] = {
        if (typedDef.isAbstractMember) return None
        clazz match {
          case wrapper: PsiClassWrapper if wrapper.definition.isInstanceOf[ScObject] =>
            return Some(wrapper) //this is static case, when containing class should be wrapper
          case _ =>
        }

        ScalaPsiUtil.nameContext(typedDef) match {
          case m: ScMember =>
            m.containingClass match {
              case t: ScTrait =>
                val linearization = MixinNodes.linearization(clazz).flatMap(tp => ScType.extractClass(tp, Some(clazz.getProject)))
                var index = linearization.indexWhere(_ == t)
                while (index >= 0) {
                  val cl = linearization(index)
                  if (!cl.isInterface) return Some(cl)
                  index -= 1
                }
                Some(clazz)
              case _ => None
            }
          case _ => None
        }
      }

      node.info.namedElement match {
        case fun: ScFunction if !fun.isConstructor =>
          val wrappers = fun.getFunctionWrappers(isStatic, isInterface = fun.isAbstractMember, concreteClassFor(fun))
          wrappers.foreach(processMethod)
          wrappers.foreach(w => processName(w.name))
        case method: PsiMethod if !method.isConstructor =>
          if (isStatic) {
            if (method.containingClass != null && method.containingClass.qualifiedName != "java.lang.Object") {
              processMethod(StaticPsiMethodWrapper.getWrapper(method, clazz))
              processName(method.getName)
            }
          }
          else {
            processMethod(method)
            processName(method.getName)
          }
        case t: ScTypedDefinition if t.isVal || t.isVar ||
          (t.isInstanceOf[ScClassParameter] && t.asInstanceOf[ScClassParameter].isCaseClassVal) =>

          PsiTypedDefinitionWrapper.processWrappersFor(t, concreteClassFor(t), node.info.name, isStatic, isInterface, processMethod, processName)
        case _ =>
      }
    }

    def namedElements: Seq[PsiNamedElement] = {
      clazz match {
        case td: ScTemplateDefinition =>
          td.members.flatMap {
            case holder: ScDeclaredElementsHolder => holder.declaredElements
            case named: ScNamedElement => Seq(named)
            case _ => Seq.empty
          }
        case _ => clazz.getFields ++ clazz.getMethods
      }
    }
  }

  implicit class PsiNamedElementExt(val named: PsiNamedElement) extends AnyVal {
    /**
     * Second match branch is for Java only.
     */
    def name: String = {
      named match {
        case nd: ScNamedElement => nd.name
        case nd => nd.getName
      }
    }
  }

  implicit class PsiModifierListOwnerExt(val member: PsiModifierListOwner) extends AnyVal{
    /**
     * Second match branch is for Java only.
     */
    def hasAbstractModifier: Boolean = {
      member match {
        case member: ScModifierListOwner => member.hasAbstractModifier
        case _ => member.hasModifierProperty(PsiModifier.ABSTRACT)
      }
    }

    /**
     * Second match branch is for Java only.
     */
    def hasFinalModifier: Boolean = {
      member match {
        case member: ScModifierListOwner => member.hasFinalModifier
        case _ => member.hasModifierProperty(PsiModifier.FINAL)
      }
    }

    /**
     * Second match branch is for Java only.
     */
    def hasModifierPropertyScala(name: String): Boolean = {
      member match {
        case member: ScModifierListOwner => member.hasModifierPropertyScala(name)
        case _ => member.hasModifierProperty(name)
      }
    }
  }

  implicit class PipedObject[T](val value: T) extends AnyVal {
    def |>[R](f: T => R) = f(value)
  }

  implicit class IteratorExt[A](val delegate: Iterator[A]) extends AnyVal {
    def findByType[T](aClass: Class[T]): Option[T] =
      delegate.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

    def filterByType[T](aClass: Class[T]): Iterator[T] =
      delegate.filter(aClass.isInstance(_)).map(_.asInstanceOf[T])
  }

  implicit class RegexExt(val regex: Regex) extends AnyVal {
    def matches(s: String) = regex.pattern.matcher(s).matches
  }

  import scala.language.implicitConversions

  implicit def toIdeaFunction[A, B](f: Function[A, B]): com.intellij.util.Function[A, B] = new com.intellij.util.Function[A, B] {
    override def fun(param: A): B = f(param)
  }

  implicit def toProcessor[T](action: T => Boolean): Processor[T] = new Processor[T] {
    override def process(t: T): Boolean = action(t)
  }

  implicit def toRunnable(action: => Any): Runnable = new Runnable {
    override def run(): Unit = action
  }

  implicit def toComputable[T](action: => T): Computable[T] = new Computable[T] {
    override def compute(): T = action
  }

  implicit def toCallable[T](action: => T): Callable[T] = new Callable[T] {
    override def call(): T = action
  }
  
  def startCommand(project: Project, commandName: String)(body: => Unit): Unit = {
    CommandProcessor.getInstance.executeCommand(project, new Runnable {
      def run() {
        inWriteAction {
          body
        }
      }
    }, commandName, null)
  }

  def inWriteAction[T](body: => T): T = {
    ApplicationManager.getApplication.runWriteAction(new Computable[T] {
      def compute: T = body
    })
  }

  def inWriteCommandAction[T](project: Project, commandName: String = "Undefined")(body: => T): T = {
    val computable = new Computable[T] {
      override def compute(): T = body
    }
    new WriteCommandAction[T](project, commandName) {
      protected def run(@NotNull result: Result[T]) {
        result.setResult(computable.compute())
      }
    }.execute.getResultObject
  }

  def inReadAction[T](body: => T): T = {
    ApplicationManager.getApplication.runReadAction(new Computable[T] {
      def compute: T = body
    })
  }

  def executeOnPooledThread[T](body: => T): Future[T] = {
    ApplicationManager.getApplication.executeOnPooledThread(toCallable(body))
  }

  def withProgressSynchronously[T](title: String)(body: ((String => Unit) => T)): T = {
    withProgressSynchronouslyTry[T](title)(body) match {
      case Success(result) => result
      case Failure(exception) => throw exception
    }
  }

  def withProgressSynchronouslyTry[T](title: String)(body: ((String => Unit) => T)): Try[T] = {
    val progressManager = ProgressManager.getInstance

    val computable  = new ThrowableComputable[T, Exception] {
      @throws(classOf[Exception])
      def compute: T = {
        val progressIndicator = progressManager.getProgressIndicator
        body(progressIndicator.setText)
      }
    }

    catching(classOf[Exception]).withTry {
      progressManager.runProcessWithProgressSynchronously(computable, title, false, null)
    }
  }

  def postponeFormattingWithin[T](project: Project)(body: => T): T = {
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable[T]{
      def compute(): T = body
    })
  }

  def withDisabledPostprocessFormatting[T](project: Project)(body: => T): T = {
    PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside {
      new Computable[T] {
        override def compute(): T = body
      }
    }
  }

  def invokeLater[T](body: => T) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        body
      }
    })
  }

  def invokeAndWait[T](body: => Unit) {
    preservingControlFlow {
      SwingUtilities.invokeAndWait(new Runnable {
        def run() {
          body
        }
      })
    }
  }

  private def preservingControlFlow(body: => Unit) {
    try {
      body
    } catch {
      case e: InvocationTargetException => e.getTargetException match {
        case control: NonLocalReturnControl[_] => throw control
        case _ => throw e
      }
    }
  }

  /** Create a PartialFunction from a sequence of cases. Workaround for pattern matcher bug */
  def pf[A, B](cases: PartialFunction[A, B]*) = new PartialFunction[A, B] {
    def isDefinedAt(x: A): Boolean = cases.exists(_.isDefinedAt(x))

    def apply(v1: A): B = {
      val it = cases.iterator
      while (it.hasNext) {
        val caze = it.next()
        if (caze.isDefinedAt(v1))
          return caze(v1)
      }
      throw new MatchError(v1.toString)
    }
  }

  implicit class PsiParameterExt(val param: PsiParameter) extends AnyVal {
    def paramType: ScType = {
      param match {
        case f: FakePsiParameter => f.parameter.paramType
        case param: ScParameter => param.getType(TypingContext.empty).getOrAny
        case _ => ScType.create(param.getType, param.getProject, param.getResolveScope, paramTopLevel = true)
      }
    }

    def exactParamType(treatJavaObjectAsAny: Boolean = true): ScType = {
      param match {
        case f: FakePsiParameter => f.parameter.paramType
        case param: ScParameter => param.getType(TypingContext.empty).getOrAny
        case _ =>
          val paramType = param.getType match {
            case p: PsiArrayType if param.isVarArgs => p.getComponentType
            case tp => tp
          }
          ScType.create(paramType, param.getProject, param.getResolveScope, paramTopLevel = true,
            treatJavaObjectAsAny = treatJavaObjectAsAny)
      }
    }

    def index: Int = {
      param match {
        case f: FakePsiParameter => f.parameter.index
        case p: ScParameter => p.index
        case _ =>
          param.getParent match {
            case pList: PsiParameterList => pList.getParameterIndex(param)
            case _ => -1
          }
      }
    }
  }

  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      if (resource != null) resource.close()
    }
  }

  def using[B](source: Source)(block: Source => B): B = {
    try {
      block(source)
    } finally {
      source.close()
    }
  }

  val ChildOf = Parent
}