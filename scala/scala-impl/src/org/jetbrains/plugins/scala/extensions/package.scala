package org.jetbrains.plugins.scala

import java.io.Closeable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{Callable, Future}

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.{ApplicationManager, Result, TransactionGuard}
import com.intellij.openapi.command.{CommandProcessor, WriteCommandAction}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, Ref, TextRange, ThrowableComputable}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.impl.source.{PostprocessReformattingAspect, PsiFileImpl}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.util.PsiTreeUtil.{getNonStrictParentOfType, getParentOfType, isAncestor}
import com.intellij.util.{ArrayFactory, Processor}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.implementation.iterator._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Seq
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer
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

    import PsiMethodExt._
    implicit private def project: ProjectContext = repr.getProject

    def isAccessor: Boolean = isParameterless && hasQueryLikeName && !hasVoidReturnType

    def isMutator: Boolean = hasVoidReturnType || hasMutatorLikeName

    def hasQueryLikeName: Boolean = {
      def startsWith(name: String, prefix: String) =
        name.length > prefix.length && name.startsWith(prefix) && name.charAt(prefix.length).isUpper

      repr.getName match {
        case "getInstance" => false // TODO others?
        case name if startsWith(name, "getAnd") || startsWith(name, "getOr") => false
        case AccessorNamePattern() => true
        case _ => false
      }
    }

    def hasMutatorLikeName: Boolean = repr.getName match {
      case MutatorNamePattern() => true
      case _ => false
    }

    def hasVoidReturnType: Boolean = repr.getReturnType == PsiType.VOID

    def parameters: Seq[PsiParameter] =
      repr.getParameterList.getParameters

    def parametersTypes: Seq[ScType] = repr match {
      case scalaFunction: ScFunction =>
        scalaFunction.parameters
          .map(_.`type`().getOrNothing)
      case _ =>
        parameters.map(_.getType)
          .map(_.toScType())
    }

    def isParameterless: Boolean =
      repr.getParameterList.getParametersCount == 0
  }

  object PsiMethodExt {
    val AccessorNamePattern: Regex =
      """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*""".r

    val MutatorNamePattern: Regex =
      """(?-i)(?:do|set|add|remove|insert|delete|aquire|release|update)(?:\p{Lu}.*)""".r
  }

  implicit class TraversableExt[CC[X] <: Traversable[X], A](val value: CC[A]) extends AnyVal {
    private type CanBuildTo[Elem, C[X]] = CanBuildFrom[Nothing, Elem, C[Elem]]

    def foreachDefined(pf: PartialFunction[A, Unit]): Unit =
      value.foreach(pf.applyOrElse(_, (_: A) => Unit))

    def filterBy[T: ClassTag](implicit cbf: CanBuildTo[T, CC]): CC[T] =
      value.filter(implicitly[ClassTag[T]].runtimeClass.isInstance).map[T, CC[T]](_.asInstanceOf[T])(collection.breakOut)

    def findBy[T: ClassTag]: Option[T] =
      value.find(implicitly[ClassTag[T]].runtimeClass.isInstance).map(_.asInstanceOf[T])

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

  implicit class ObjectExt[T](val v: T) extends AnyVal {
    def toOption: Option[T] = Option(v)

    def asOptionOf[E: ClassTag]: Option[E] = {
      if (classTag[E].runtimeClass.isInstance(v)) Some(v.asInstanceOf[E])
      else None
    }

    def getOrElse[H >: T](default: H): H = if (v == null) default else v

    def collectOption[B](pf: scala.PartialFunction[T, B]): Option[B] = Some(v).collect(pf)
  }

  implicit class OptionExt[T](val option: Option[T]) extends AnyVal {
    def getOrThrow(exception: => Exception): T = option.getOrElse(throw exception)
  }

  implicit class BooleanExt(val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if (b) Some(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: => A): Seq[A] = if (b) Seq(a) else Seq.empty

    // looks better withing expressions than { if (???) ??? else ??? } block
    def fold[T](ifTrue: => T, ifFalse: => T): T = if (b) ifTrue else ifFalse

    def toInt: Int = if (b) 1 else 0
  }

  implicit class StringExt(val string: String) extends AnyVal {
    def parenthesize(needParenthesis: Boolean): String =
      if (needParenthesis) s"($string)" else string
  }

  implicit class CharSeqExt(val cs: CharSequence) extends AnyVal {
    private def iterator = new Iterator[Char] {
      var idx = 0

      override def hasNext: Boolean = idx < cs.length()

      override def next(): Char = {
        idx += 1
        cs.charAt(idx - 1)
      }
    }

    def count(pred: Char => Boolean): Int = iterator.count(pred)

    def prefixLength(pred: Char => Boolean): Int = iterator.takeWhile(pred).size

    def startsWith(s: String): Boolean = cs.substring(0, s.length) == s

    def substring(start: Int, end: Int): String =
      cs.subSequence(start, end).toString

    def substring(range: TextRange): String =
      cs.subSequence(range.getStartOffset, range.getEndOffset).toString
  }

  implicit class StringsExt(val strings: Seq[String]) extends AnyVal {
    def commaSeparated: String =
      strings.mkString(", ")
  }

  implicit class ASTNodeExt(val node: ASTNode) extends AnyVal {
    def hasChildOfType(elementType: IElementType): Boolean =
      node.findChildByType(elementType) != null
  }

  implicit class PsiElementExt[E <: PsiElement](val element: E) extends AnyVal {
    def startOffsetInParent: Int =
      element match {
        case s: ScalaPsiElement => s.startOffsetInParent
        case _ => element.getStartOffsetInParent
      }

    implicit def elementScope: ElementScope = ElementScope(element)

    def projectContext: ProjectContext = element.getProject

    def ofNamedElement(substitutor: ScSubstitutor = ScSubstitutor.empty): Option[ScType] = {
      def lift(`type`: PsiType) = Option(`type`.toScType())

      (element match {
        case _: ScPrimaryConstructor => None
        case e: ScFunction if e.isConstructor => None
        case e: ScFunction => e.returnType.toOption
        case e: ScBindingPattern => e.`type`().toOption
        case e: ScFieldId => e.`type`().toOption
        case e: ScParameter => e.getRealParameterType.toOption
        case e: PsiMethod if e.isConstructor => None
        case e: PsiMethod => lift(e.getReturnType)
        case e: PsiVariable => lift(e.getType)
        case _ => None
      }).map {
        substitutor.subst
      }
    }

    def firstChild: Option[PsiElement] = Option(element.getFirstChild)

    def lastChild: Option[PsiElement] = Option(element.getLastChild)

    def containingFile: Option[PsiFile] = Option(element.getContainingFile)

    def containingScalaFile: Option[ScalaFile] = element.containingFile.collect {
      case file: ScalaFile => file
    }

    def containingVirtualFile: Option[VirtualFile] = containingFile.flatMap { file =>
      Option(file.getVirtualFile)
    }

    def parent: Option[PsiElement] = Option(element.getParent)

    def parentOfType[E <: PsiElement](clazz: Class[E], strict: Boolean = true): Option[E] =
      Option(getParentOfType(element, clazz, strict))

    def parentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getParentOfType(element, classes: _*))

    def nonStrictParentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getNonStrictParentOfType(element, classes: _*))

    def isAncestorOf(otherElement: PsiElement): Boolean = isAncestor(element, otherElement, true)

    def parents: Iterator[PsiElement] = new ParentsIterator(element)

    def withParents: Iterator[PsiElement] = new ParentsIterator(element, strict = false)

    def parentsInFile: Iterator[PsiElement] = element match {
      case _: PsiFile | _: PsiDirectory => Iterator.empty
      case _ => parents.takeWhile(!_.isInstanceOf[PsiFile])
    }

    def withParentsInFile: Iterator[PsiElement] = Iterator(element) ++ parentsInFile

    def children: Iterator[PsiElement] = new ChildrenIterator(element)

    def depthFirst(predicate: PsiElement => Boolean = _ => true): Iterator[PsiElement] =
      new DepthFirstIterator(element, predicate)

    def breadthFirst(predicate: PsiElement => Boolean = _ => true): Iterator[PsiElement] =
      new BreadthFirstIterator(element, predicate)

    def prevSibling: Option[PsiElement] = Option(element.getPrevSibling)

    def nextSibling: Option[PsiElement] = Option(element.getNextSibling)

    def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(element)

    def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(element)

    def nextSibilingsWithSelf: Iterator[PsiElement] = Iterator(element) ++ nextSiblings

    def contexts: Iterator[PsiElement] = new ContextsIterator(element)

    def withContexts: Iterator[PsiElement] = new ContextsIterator(element, strict = false)

    def scopes: Iterator[PsiElement] = contexts.filter(ScalaPsiUtil.isScope)

    def getPrevSiblingNotWhitespace: PsiElement = {
      var prev: PsiElement = element.getPrevSibling
      while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
        prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) prev = prev.getPrevSibling
      prev
    }

    def getPrevSiblingNotWhitespaceComment: PsiElement = {
      var prev: PsiElement = element.getPrevSibling
      while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
        prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || prev.isInstanceOf[PsiComment]))
        prev = prev.getPrevSibling
      prev
    }

    def getNextSiblingNotWhitespace: PsiElement = {
      var next: PsiElement = element.getNextSibling
      while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
        next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) next = next.getNextSibling
      next
    }

    def getNextSiblingNotWhitespaceComment: PsiElement = {
      var next: PsiElement = element.getNextSibling
      while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
        next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || next.isInstanceOf[PsiComment]))
        next = next.getNextSibling
      next
    }

    def resolveScope: GlobalSearchScope =
      if (element.isValid) element.getResolveScope
      else GlobalSearchScope.EMPTY_SCOPE

    def prependSiblings(elements: PsiElement*): Seq[PsiElement] = {
      val parent = element.getParent
      elements.foldLeft(Seq.empty[PsiElement])(_ :+ parent.addBefore(_, element))
    }

    def appendSiblings(elements: PsiElement*): Seq[PsiElement] = {
      val parent = element.getParent
      elements.foldRight(List.empty[PsiElement])(parent.addAfter(_, element) :: _)
    }

    def createSmartPointer(implicit manager: SmartPointerManager = SmartPointerManager.getInstance(element.getProject)): SmartPsiElementPointer[E] =
      manager.createSmartPsiElementPointer(element)
  }

  implicit class PsiTypeExt(val `type`: PsiType) extends AnyVal {
    def toScType(visitedRawTypes: Set[PsiClass] = Set.empty,
                 paramTopLevel: Boolean = false,
                 treatJavaObjectAsAny: Boolean = true)
                (implicit project: ProjectContext): ScType =
      project.typeSystem.toScType(`type`, treatJavaObjectAsAny)(visitedRawTypes, paramTopLevel)
  }

  implicit class PsiWildcardTypeExt(val `type`: PsiWildcardType) extends AnyVal {
    def lower(implicit project: ProjectContext,
              visitedRawTypes: Set[PsiClass],
              paramTopLevel: Boolean): Option[ScType] =
      bound(if (`type`.isSuper) Some(`type`.getSuperBound) else None)

    def upper(implicit project: ProjectContext,
              visitedRawTypes: Set[PsiClass],
              paramTopLevel: Boolean): Option[ScType] =
      bound(if (`type`.isExtends) Some(`type`.getExtendsBound) else None)

    private def bound(maybeBound: Option[PsiType])
                     (implicit project: ProjectContext,
                      visitedRawTypes: Set[PsiClass],
                      paramTopLevel: Boolean) = maybeBound map {
      _.toScType(visitedRawTypes, paramTopLevel = paramTopLevel)
    }
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

    def constructors: Seq[PsiMethod] =
      clazz match {
        case c: ScConstructorOwner => c.constructors
        case _ => clazz.getConstructors
      }

    def isEffectivelyFinal: Boolean = clazz match {
      case scClass: ScClass => scClass.hasFinalModifier
      case _: ScObject | _: ScNewTemplateDefinition => true
      case synth: ScSyntheticClass if !Seq("AnyRef", "AnyVal").contains(synth.className) => true //wrappers for value types
      case _ => clazz.hasModifierProperty(PsiModifier.FINAL)
    }

    def allSupers: Seq[PsiClass] = {
      val res = ArrayBuffer[PsiClass]()

      def addWithSupers(c: PsiClass): Unit = {
        if (!res.contains(c)) {
          if (c != clazz) res += c
          c.getSupers.foreach(addWithSupers)
        }
      }

      addWithSupers(clazz)
      res.toVector
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
              case t: ScTrait if isStatic =>
                val linearization = MixinNodes.linearization(clazz)
                  .flatMap(_.extractClass)
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

    def nameContext: PsiElement = {
      named match {
        case sc: ScNamedElement => sc.nameContext
        case _ =>
          named.withParentsInFile
            .find(ScalaPsiUtil.isNameContext)
            .orNull
      }
    }
  }

  implicit class PsiModifierListOwnerExt(val member: PsiModifierListOwner) extends AnyVal {
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
    def |>[R](f: T => R): R = f(value)
  }

  implicit class IteratorExt[A](val delegate: Iterator[A]) extends AnyVal {
    def findByType[T: ClassTag]: Option[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.find(aClass.isInstance).map(_.asInstanceOf[T])
    }

    def filterByType[T: ClassTag]: Iterator[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.filter(aClass.isInstance).map(_.asInstanceOf[T])
    }

    def containsType[T: ClassTag]: Boolean = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.exists(aClass.isInstance)
    }

    def headOption: Option[A] = {
      if (delegate.hasNext) Some(delegate.next())
      else None
    }
  }

  implicit class ConcurrentMapExt[K, V](val map: java.util.concurrent.ConcurrentMap[K, V]) extends AnyVal {

    //getOrElseUpdate in JConcurrentMapWrapper is not atomic!
    def atomicGetOrElseUpdate(key: K, update: => V): V = {
      Option(map.get(key)) match {
        case Some(v) => v
        case None =>
          val newValue = update
          val race = map.putIfAbsent(key, newValue)

          if (race != null) race
          else newValue
      }
    }
  }

  implicit class RegexExt(val regex: Regex) extends AnyVal {
    def matches(s: String): Boolean = regex.pattern.matcher(s).matches
  }

  import scala.language.implicitConversions

  implicit def toIdeaFunction[A, B](f: Function[A, B]): com.intellij.util.Function[A, B] = (param: A) => f(param)

  implicit def toProcessor[T](action: T => Boolean): Processor[T] = (t: T) => action(t)

  implicit def toRunnable(action: => Any): Runnable = () => action

  implicit def toComputable[T](action: => T): Computable[T] = () => action

  implicit def toCallable[T](action: => T): Callable[T] = () => action

  def startCommand(project: Project, runnable: Runnable, commandName: String): Unit =
    CommandProcessor.getInstance().executeCommand(project, runnable, commandName, null)

  def startCommand(project: Project, commandName: String = "")(body: => Unit): Unit = {
    startCommand(project, () => inWriteAction(body), commandName)
  }

  def inWriteAction[T](body: => T): T = {
    val application = ApplicationManager.getApplication

    if (application.isWriteAccessAllowed) body
    else {
      application.runWriteAction(
        new Computable[T] {
          def compute: T = body
        }
      )
    }
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
    val application = ApplicationManager.getApplication

    if (application.isReadAccessAllowed) body
    else {
      application.runReadAction(
        new Computable[T] {
          override def compute(): T = body
        }
      )
    }
  }

  //use only for defining toString method
  def ifReadAllowed[T](body: => T)(default: => T): T = {
    try {
      val ref: Ref[T] = Ref.create()
      ProgressManager.getInstance().executeNonCancelableSection {
        ref.set(ApplicationUtil.tryRunReadAction(body))
      }
      ref.get()
    } catch {
      case _: ProcessCanceledException => default
    }
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

    val computable = new ThrowableComputable[T, Exception] {
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

  def postponeFormattingWithin[T](project: Project)(body: => T): T =
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(body)

  def withDisabledPostprocessFormatting[T](project: Project)(body: => T): T =
    PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(body)

  def invokeLater[T](body: => T): Unit =
    ApplicationManager.getApplication.invokeLater(() => body)

  def invokeAndWait[T](body: => T): Unit =
    preservingControlFlow {
      ApplicationManager.getApplication.invokeAndWait(() => body)
    }

  def callbackInTransaction(disposable: Disposable)(body: => Unit): Runnable = {
    TransactionGuard.getInstance().submitTransactionLater(disposable, body)
  }

  def invokeAndWaitInTransaction(disposable: Disposable)(body: => Unit): Unit = {
    TransactionGuard.getInstance().submitTransactionAndWait(disposable, body)
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
  def pf[A, B](cases: PartialFunction[A, B]*): PartialFunction[A, B] = new PartialFunction[A, B] {
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
    implicit def project: ProjectContext = param.getProject

    def paramType(exact: Boolean = true, treatJavaObjectAsAny: Boolean = true): ScType = param match {
      case parameter: FakePsiParameter => parameter.parameter.paramType
      case parameter: ScParameter => parameter.`type`().getOrAny
      case _ =>
        val paramType = param.getType match {
          case arrayType: PsiArrayType if exact && param.isVarArgs =>
            arrayType.getComponentType
          case tp => tp
        }
        paramType.toScType(paramTopLevel = true, treatJavaObjectAsAny = treatJavaObjectAsAny)
    }

    def index: Int = param match {
      case parameter: FakePsiParameter => parameter.parameter.index
      case parameter: ScParameter => parameter.index
      case _ => param.getParent match {
        case list: PsiParameterList => list.getParameterIndex(param)
        case _ => -1
      }
    }
  }

  implicit class StubBasedExt(val element: PsiElement) extends AnyVal {
    def stubOrPsiChildren[Psi <: PsiElement, Stub <: StubElement[_ <: Psi]](elementType: IStubElementType[Stub, Psi], f: ArrayFactory[Psi]): Array[Psi] = {
      def findWithNode(): Array[Psi] = {
        val nodes = SharedImplUtil.getChildrenOfType(element.getNode, elementType)
        val length = nodes.length
        val array = f.create(length)
        var i = 0
        while (i < length) {
          array(i) = nodes(i).getPsi.asInstanceOf[Psi]
          i += 1
        }
        array
      }

      element match {
        case st: StubBasedPsiElementBase[_] => st.getStubOrPsiChildren(elementType, f)
        case file: PsiFileImpl =>
          file.getGreenStub match {
            case stub: StubElement[_] => stub.getChildrenByType(elementType, f)
            case null => findWithNode()
          }
        case _ => findWithNode()
      }
    }

    def stubOrPsiChildren[Psi <: PsiElement](filter: TokenSet, f: ArrayFactory[Psi]): Array[Psi] = {
      def findWithNode(): Array[Psi] = {
        val nodes = element.getNode.getChildren(filter)
        val length = nodes.length
        val array = f.create(length)
        var i = 0
        while (i < length) {
          array(i) = nodes(i).getPsi.asInstanceOf[Psi]
          i += 1
        }
        array
      }

      element match {
        case st: StubBasedPsiElementBase[_] => st.getStubOrPsiChildren(filter, f)
        case file: PsiFileImpl =>
          file.getGreenStub match {
            case stub: StubElement[_] => stub.getChildrenByType(filter, f)
            case null => findWithNode()
          }
        case _ => findWithNode()
      }
    }

    def stubOrPsiChild[Psi <: PsiElement, Stub <: StubElement[_ <: Psi]](elementType: IStubElementType[Stub, Psi]): Option[Psi] = {
      def findWithNode() = {
        val node = Option(element.getNode.findChildByType(elementType))
        node.map(_.getPsi.asInstanceOf[Psi])
      }

      element match {
        case st: StubBasedPsiElementBase[_] => Option(st.getStubOrPsiChild(elementType))
        case file: PsiFileImpl =>
          file.getGreenStub match {
            case stub: StubElement[_] => Option(stub.findChildStubByType(elementType)).map(_.getPsi)
            case _ => findWithNode()
          }
        case _ => findWithNode()
      }
    }

    def greenStub: Option[StubElement[_]] = element match {
      case st: StubBasedPsiElementBase[_] => Option(st.getGreenStub.asInstanceOf[StubElement[_]])
      case file: PsiFileImpl => Option(file.getGreenStub)
      case _ => None
    }

    def stub: Option[StubElement[_]] = element match {
      case st: StubBasedPsiElementBase[_] => Option(st.getStub.asInstanceOf[StubElement[_]])
      case file: PsiFileImpl => Option(file.getStub)
      case _ => None
    }

    def lastChildStub: Option[PsiElement] = {
      val children = stubOrPsiChildren(TokenSet.ANY, PsiElement.ARRAY_FACTORY)
      val size = children.length
      if (size == 0) None
      else Some(children(size - 1))
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

  /* Calls each funtion with `v` as an argument, returns `v` (replicates Kotlin's "apply").
     Useful to avoid defining a temporary variable and then repeating its name.
     See also: |>
     TODO: convert to a macro
    */
  def applyTo[T](v: T)(fs: (T => Any)*): T = {
    fs.foreach(_.apply(v))
    v
  }

  val ChildOf: Parent.type = Parent
}
