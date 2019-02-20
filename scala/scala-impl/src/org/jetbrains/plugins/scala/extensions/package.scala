package org.jetbrains.plugins.scala

import java.io.Closeable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{Callable, Future}
import java.util.regex.Pattern

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.{ApplicationManager, TransactionGuard}
import com.intellij.openapi.command.{CommandProcessor, UndoConfirmationPolicy, WriteCommandAction}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, Ref, TextRange, ThrowableComputable}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.impl.source.{PostprocessReformattingAspect, PsiFileImpl}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.{ArrayFactory, Processor}
import org.jetbrains.plugins.scala.extensions.implementation.iterator._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScFieldId}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.collection.Seq
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.language.higherKinds
import scala.reflect.{ClassTag, classTag}
import scala.runtime.NonLocalReturnControl
import scala.util.control.Exception.catching
import scala.util.{Failure, Success, Try}

/**
  * Pavel Fatin
  */

package object extensions {

  val Placeholder = "_"

  implicit class PsiMethodExt(val repr: PsiMethod) extends AnyVal {

    import PsiMethodExt._

    implicit private def project: ProjectContext = repr.getProject

    def isAccessor: Boolean = isParameterless &&
      hasQueryLikeName &&
      repr.getReturnType != PsiType.VOID

    def hasQueryLikeName: Boolean = {
      val name = repr.getName

      def startsWith(prefix: String) =
        name.length > prefix.length && name.startsWith(prefix) && name.charAt(prefix.length).isUpper

      name != "getInstance" && // TODO others?
        AccessorNamePattern.matcher(name).matches() &&
        !startsWith("getAnd") &&
        !startsWith("getOr")
    }

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

    def functionType(implicit scope: ElementScope): TypeResult = repr match {
      case sf: ScFunction => sf.`type`()
      case _ =>
        val retTpe = repr.getReturnType.toScType()
        Right(FunctionType((retTpe, parametersTypes)))
    }
  }

  implicit class PsiFileExt(val file: PsiFile) extends AnyVal {
    def charSequence: CharSequence =
      if (file.isValid) file.getViewProvider.getContents
      else file.getText
  }

  object PsiMethodExt {
    private val AccessorNamePattern = Pattern.compile(
      """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*"""
    )
  }

  implicit class TraversableExt[CC[X] <: Traversable[X], A](val value: CC[A]) extends AnyVal {
    private type CanBuildTo[Elem, C[X]] = CanBuildFrom[Nothing, Elem, C[Elem]]

    def foreachDefined(pf: PartialFunction[A, Unit]): Unit =
      value.foreach(pf.applyOrElse(_, (_: A) => Unit))

    def filterBy[T: ClassTag](implicit cbf: CanBuildTo[T, CC]): CC[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      value.filter(clazz.isInstance).map[T, CC[T]](_.asInstanceOf[T])(collection.breakOut)
    }

    def findBy[T: ClassTag]: Option[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      value.find(clazz.isInstance).asInstanceOf[Option[T]]
    }

    def findFirstBy[T: ClassTag](predicate: T => Boolean): Option[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      val result = value.find {
        case elem if clazz.isInstance(elem) && predicate(elem.asInstanceOf[T]) => true
        case _ => false
      }
      result.asInstanceOf[Option[T]]
    }

    def mkParenString(implicit ev: A <:< String): String = value.mkString("(", ", ", ")")
  }

  implicit class SeqExt[CC[X] <: Seq[X], A <: AnyRef](val value: CC[A]) extends AnyVal {
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

    //may return same instance if no element was changed
    def smartMapWithIndex(f: (A, Int) => A)(implicit cbf: CanBuildTo[A, CC]): CC[A] = {
      val b = cbf()
      val iterator = value.iterator
      var i = 0
      var updated = false
      while (iterator.hasNext) {
        val next = iterator.next()
        val fNext = f(next, i)
        if (next ne fNext) {
          updated = true
        }
        b += fNext
        i += 1
      }
      if (updated) b.result()
      else value
    }


    def foreachWithIndex[B](f: (A, Int) => B) {
      var i = 0
      for (x <- value) {
        f(x, i)
        i += 1
      }
    }

    def intersperse[B >: A](sep: B): Seq[B] = value.iterator.intersperse(sep).toSeq
  }

  implicit class IterableExt[CC[X] <: Iterable[X], A <: AnyRef](val value: CC[A]) extends AnyVal {
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

    def mapToArray[B <: AnyRef](f: A => B)(implicit factory: ArrayFactory[B]): Array[B] = {
      val size = value.size
      val array = factory.create(size)
      val iterator = value.iterator
      var idx = 0
      while (iterator.hasNext) {
        array(idx) = f(iterator.next)
        idx += 1
      }
      array
    }

    //may return same instance if no element was changed
    def smartMap(f: A => A)(implicit cbf: CanBuildTo[A, CC]): CC[A] = {
      val b = cbf()
      val iterator = value.iterator
      var updated = false
      while (iterator.hasNext) {
        val next = iterator.next()
        val fNext = f(next)
        if (next ne fNext) {
          updated = true
        }
        b += fNext
      }
      if (updated) b.result()
      else value
    }

  }

  implicit class ArrayExt[A](val array: Array[A]) extends AnyVal {
    def findByType[T: ClassTag]: Option[T] = collectFirstByType(identity[T])

    def collectFirstByType[T: ClassTag, R](f: T => R): Option[R] = {
      var idx = 0
      val clazz = implicitly[ClassTag[T]].runtimeClass
      while (idx < array.length) {
        val element = array(idx)
        if (clazz.isInstance(element)) {
          return Some(f(element.asInstanceOf[T]))
        }
        idx += 1
      }
      None
    }

    //changes content of a current array!
    def updateContent(f: Int => A): array.type = {
      var idx = 0
      while (idx < array.length) {
        array(idx) = f(idx)
        idx += 1
      }
      array
    }
  }

  implicit class ToNullSafe[+A >: Null](val a: A) extends AnyVal {
    def nullSafe = NullSafe(a)
  }

  implicit class OptionToNullSafe[+A >: Null](val a: Option[A]) extends AnyVal {
    //to handle Some(null) case and avoid wrapping of intermediate function results
    //in chained map/flatMap calls
    def toNullSafe = NullSafe(a.orNull)
  }

  implicit class ObjectExt[T](val v: T) extends AnyVal {
    def toOption: Option[T] = Option(v)

    def asOptionOf[E: ClassTag]: Option[E] = {
      if (classTag[E].runtimeClass.isInstance(v)) Some(v.asInstanceOf[E])
      else None
    }

    def ifNot(predicate: T => Boolean): Option[T] =
      if (predicate(v)) None else Some(v)
  }

  implicit class OptionExt[T](val option: Option[T]) extends AnyVal {
    def getOrThrow(exception: => Exception): T = option.getOrElse(throw exception)

    def filterByType[S: ClassTag]: Option[S] = {
      option match {
        case Some(element) if implicitly[ClassTag[S]].runtimeClass.isInstance(element) =>
          option.asInstanceOf[Option[S]]
        case _ => None
      }
    }
  }

  implicit class BooleanExt(val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if (b) Option(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: => A): Seq[A] = if (b) Seq(a) else Seq.empty

    // looks better withing expressions than { if (???) ??? else ??? } block
    def fold[T](ifTrue: => T, ifFalse: => T): T = if (b) ifTrue else ifFalse

    def toInt: Int = if (b) 1 else 0
  }

  implicit class IntArrayExt(val array: Array[Int]) extends AnyVal {
    import java.util.Arrays

    def ===(other: Array[Int]): Boolean = Arrays.equals(array, other)

    def =!=(other: Array[Int]): Boolean = !Arrays.equals(array, other)

    def arraySum: Int = {
      var idx, res = 0
      while (idx < array.length) {
        res += array(idx)
        idx += 1
      }
      res
    }

    def hash: Int = Arrays.hashCode(array)
  }

  implicit class StringExt(val string: String) extends AnyVal {

    def parenthesize(needParenthesis: Boolean = true): String =
      if (needParenthesis) s"($string)" else string
  }

  implicit class CharSeqExt(val cs: CharSequence) extends AnyVal {
    private def iterator: Iterator[Char] = new Iterator[Char] {
      var idx = 0

      override def hasNext: Boolean = idx < cs.length()

      override def next(): Char = {
        idx += 1
        cs.charAt(idx - 1)
      }
    }

    def count(pred: Char => Boolean): Int = iterator.count(pred)

    def prefixLength(pred: Char => Boolean): Int = iterator.takeWhile(pred).size

    def startsWith(prefix: String): Boolean =
      prefix.length <= cs.length && cs.substring(0, prefix.length) == prefix

    def endsWith(suffix: String): Boolean =
      suffix.length <= cs.length && cs.substring(cs.length() - suffix.length) == suffix

    def substring(start: Int, end: Int = cs.length()): String =
      cs.subSequence(start, end).toString

    def substring(range: TextRange): String =
      cs.subSequence(range.getStartOffset, range.getEndOffset).toString

    def indexOf(pattern: CharSequence, fromIndex: Int = 0): Int =
      CharArrayUtil.indexOf(cs, pattern, fromIndex)
  }

  implicit class StringsExt(val strings: Seq[String]) extends AnyVal {
    def commaSeparated(model: Model.Val = Model.None): String =
      strings.mkString(model.start, ", ", model.end)
  }

  object Model extends Enumeration {

    class Val(val start: String, val end: String) extends super.Val()

    val None = new Val("", "")
    val Parentheses = new Val("(", ")")
    val Braces = new Val("{", "}")
    val SquareBrackets = new Val("[", "]")
  }

  implicit class PsiElementExt[E <: PsiElement](val element: E) extends AnyVal {
    def startOffsetInParent: Int =
      element match {
        case s: ScalaPsiElement => s.startOffsetInParent
        case _ => element.getStartOffsetInParent
      }

    implicit def elementScope: ElementScope = ElementScope(element)

    def projectContext: ProjectContext = element.getProject

    def ofNamedElement(substitutor: ScSubstitutor = ScSubstitutor.empty, scalaScope: Option[ElementScope] = None): Option[ScType] = {
      def lift(`type`: PsiType) = Option(`type`.toScType())

      val scope = scalaScope.getOrElse(elementScope)
      (element match {
        case Constructor(_)      => None
        case e: ScFunction       => e.`type`().toOption
        case e: ScBindingPattern => e.`type`().toOption
        case e: ScFieldId        => e.`type`().toOption
        case e: ScParameter      => e.getRealParameterType.toOption
        case e: PsiMethod        => e.functionType(scope).toOption
        case e: PsiVariable      => lift(e.getType)
        case _                   => None
      }).map(substitutor)
    }

    def firstChild: Option[PsiElement] = Option(element.getFirstChild)

    def lastChild: Option[PsiElement] = Option(element.getLastChild)

    def containingFile: Option[PsiFile] = Option(element.getContainingFile)

    def containingScalaFile: Option[ScalaFile] = element.getContainingFile match {
      case file: ScalaFile => Some(file)
      case _ => None
    }

    def containingVirtualFile: Option[VirtualFile] = element.getContainingFile match {
      case null => None
      case file => Option(file.getVirtualFile)
    }

    def sameElementInContext: PsiElement = element match {
      case sc: ScalaPsiElement => sc.getDeepSameElementInContext
      case _ => element
    }

    def parent: Option[PsiElement] = Option(element.getParent)

    import PsiTreeUtil._

    def parentOfType[Psi <: PsiElement](clazz: Class[Psi], strict: Boolean = true): Option[Psi] =
      Option(getParentOfType(element, clazz, strict))

    def parentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getParentOfType(element, classes: _*))


    def nonStrictParentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getNonStrictParentOfType(element, classes: _*))

    def findContextOfType[Psi <: PsiElement](clazz: Class[Psi]): Option[Psi] =
      Option(getContextOfType(element, clazz))

    def isAncestorOf(otherElement: PsiElement): Boolean = isAncestor(element, otherElement, true)

    def parents: Iterator[PsiElement] = new ParentsIterator(element)

    def withParents: Iterator[PsiElement] = new ParentsIterator(element, strict = false)

    def parentsInFile: Iterator[PsiElement] = element match {
      case _: PsiFile | _: PsiDirectory => Iterator.empty
      case _ => parents.takeWhile(!_.isInstanceOf[PsiFile])
    }

    def withParentsInFile: Iterator[PsiElement] = Iterator(element) ++ parentsInFile

    def children: Iterator[PsiElement] = new ChildrenIterator(element)

    def elements: Iterator[PsiElement] = depthFirst()

    def depthFirst(predicate: PsiElement => Boolean = _ => true): Iterator[PsiElement] =
      new DepthFirstIterator(element, predicate)

    def breadthFirst(predicate: PsiElement => Boolean = _ => true): Iterator[PsiElement] =
      new BreadthFirstIterator(element, predicate)

    def prevSibling: Option[PsiElement] = Option(element.getPrevSibling)

    def nextSibling: Option[PsiElement] = Option(element.getNextSibling)

    def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(element)

    def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(element)

    def withNextSiblings: Iterator[PsiElement] = Iterator(element) ++ nextSiblings

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

    def getFirstChildNotWhitespace: PsiElement = {
      element.getFirstChild match {
        case ws: PsiWhiteSpace => ws.getNextSiblingNotWhitespace
        case child => child
      }
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

    def createSmartPointer: SmartPsiElementPointer[E] =
      SmartPointerManager.getInstance(element.getProject).createSmartPsiElementPointer(element)
  }

  implicit class PsiTypeExt(val `type`: PsiType) extends AnyVal {
    def toScType(visitedRawTypes: Set[PsiClass] = Set.empty,
                 paramTopLevel: Boolean = false,
                 treatJavaObjectAsAny: Boolean = true)
                (implicit project: ProjectContext): ScType =
      project.typeSystem.toScType(`type`, treatJavaObjectAsAny)(visitedRawTypes, paramTopLevel)
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

    def names: Seq[String] = {
      member match {
        case decls: ScDeclaredElementsHolder => decls.declaredNames
        case named: PsiNamedElement          => Seq(named.name)
        case _                               => Seq.empty
      }
    }
  }

  implicit class PsiClassExt(val clazz: PsiClass) extends AnyVal {

    def isSealed: Boolean = clazz match {
      case _: ScClass | _: ScTrait =>
        clazz.asInstanceOf[ScModifierListOwner].hasModifierPropertyScala("sealed")
      case _ => false
    }

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

      //search for a class to place implementation of trait's method
      def concreteForTrait(t: ScTrait): Option[PsiClass] = {
        val fromLessConcrete =
          MixinNodes.linearization(clazz)
            .flatMap(_.extractClass)
            .reverse

        val index = fromLessConcrete.indexOf(t)
        fromLessConcrete
          .drop(index + 1)
          .filterNot(_.isInterface)
          .headOption
      }

      def concreteClassFor(typedDef: ScTypedDefinition): Option[PsiClass] = {
        if (typedDef.isAbstractMember) return None
        clazz match {
          case PsiClassWrapper(_: ScObject) =>
            return Some(clazz) //this is static case, when containing class should be wrapper
          case _ =>
        }

        ScalaPsiUtil.nameContext(typedDef) match {
          case m: ScMember =>
            m.containingClass match {
              case _: ScTrait if isStatic =>
                Some(clazz) //companion object extends some trait, static method generated in a companion class
              case t: ScTrait =>
                concreteForTrait(t)
              case _ => None
            }
          case _ => None
        }
      }

      if (!node.info.namedElement.isValid)
        return

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

    def sameOrInheritor(other: PsiClass): Boolean = areClassesEquivalent(clazz, other) || isInheritorDeep(clazz, other)
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

    import PsiModifier._

    def hasAbstractModifier: Boolean =
      hasModifierPropertyScala(ABSTRACT)

    def hasFinalModifier: Boolean =
      hasModifierPropertyScala(FINAL)

    /**
      * Second match branch is for Java only.
      */
    def hasModifierPropertyScala(name: String): Boolean = member match {
      case member: ScModifierListOwner => member.hasModifierPropertyScala(name)
      case _ => member.hasModifierProperty(name)
    }

    def setModifierProperty(name: String,
                            value: Boolean = true): Unit =
      member.getModifierList.nullSafe.foreach {
        _.setModifierProperty(name, value)
      }

  }

  implicit class PipedObject[T](val value: T) extends AnyVal {
    def |>[R](f: T => R): R = f(value)
  }

  implicit class IteratorExt[A](val delegate: Iterator[A]) extends AnyVal {
    def instanceOf[T: ClassTag]: Option[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.find(aClass.isInstance).map(_.asInstanceOf[T])
    }

    def instancesOf[T: ClassTag]: Iterator[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.filter(aClass.isInstance).map(_.asInstanceOf[T])
    }

    def containsInstanceOf[T: ClassTag]: Boolean = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.exists(aClass.isInstance)
    }

    def headOption: Option[A] = {
      if (delegate.hasNext) Some(delegate.next())
      else None
    }

    def findBy[T: ClassTag]: Option[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      delegate.find(clazz.isInstance).asInstanceOf[Option[T]]
    }

    def intersperse[B >: A](sep: B): Iterator[B] = new Iterator[B] {
      private var intersperseNext = false

      override def hasNext: Boolean = intersperseNext || delegate.hasNext

      override def next(): B = {
        val element = if (intersperseNext) sep else delegate.next()
        intersperseNext = !intersperseNext && delegate.hasNext
        element
      }
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

  import scala.language.implicitConversions

  implicit def toIdeaFunction[A, B](f: Function[A, B]): com.intellij.util.Function[A, B] = (param: A) => f(param)

  implicit def toProcessor[T](action: T => Boolean): Processor[T] = (t: T) => action(t)

  implicit def toRunnable(action: => Any): Runnable = () => action

  implicit def toComputable[T](action: => T): Computable[T] = () => action

  implicit def toCallable[T](action: => T): Callable[T] = () => action

  def startCommand(commandName: String = null)
                  (body: => Unit)
                  (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      () => body,
      commandName,
      null
    )

  def executeWriteActionCommand(commandName: String = "",
                                policy: UndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT)
                               (body: => Unit)
                               (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      () => inWriteAction(body),
      commandName,
      null,
      policy
    )

  def executeWriteActionCommand(runnable: Runnable,
                                commandName: String,
                                policy: UndoConfirmationPolicy)
                               (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      () => WriteCommandAction.runWriteCommandAction(project, runnable),
      commandName,
      null,
      policy
    )

  def inWriteAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isWriteAccessAllowed => body
    case application => application.runWriteAction(body)
  }

  def inWriteCommandAction[T](body: => T)
                             (implicit project: Project): T =
    WriteCommandAction.runWriteCommandAction(project, body)

  def inReadAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(body)
  }

  //use only for defining toString method
  def ifReadAllowed[T](body: => T)(default: => T): T = {
    try {
      val ref = Ref.create[T]
      ProgressManager.getInstance().executeNonCancelableSection {
        ref.set(ApplicationUtil.tryRunReadAction(body))
      }
      ref.get
    } catch {
      case _: ProcessCanceledException => default
    }
  }

  def executeOnPooledThread[T](body: => T): Future[T] =
    ApplicationManager.getApplication.executeOnPooledThread(body)

  def withProgressSynchronously[T](title: String)(body: => T): T = {
    withProgressSynchronouslyTry[T](title)(_ => body) match {
      case Success(result) => result
      case Failure(exception) => throw exception
    }
  }

  def withProgressSynchronouslyTry[T](title: String)(body: ProgressManager => T): Try[T] = {
    val manager = ProgressManager.getInstance
    catching(classOf[Exception]).withTry {
      manager.runProcessWithProgressSynchronously(new ThrowableComputable[T, Exception] {
        def compute: T = body(manager)
      }, title, false, null)
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
    def stubOrPsiChildren[Psi <: PsiElement, Stub <: StubElement[_ <: Psi]](elementType: IStubElementType[Stub, _ <: Psi], f: ArrayFactory[Psi]): Array[Psi] = {
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

    def stubOrPsiChild[Psi <: PsiElement, Stub <: StubElement[Psi]](elementType: IStubElementType[Stub, Psi]): Option[Psi] = {
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
