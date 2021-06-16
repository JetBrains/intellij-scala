package org.jetbrains.plugins.scala

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.{ApplicationManager, ModalityState, TransactionGuard}
import com.intellij.openapi.command.{CommandProcessor, UndoConfirmationPolicy, WriteCommandAction}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.impl.source.{PostprocessReformattingAspect, PsiFileImpl}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.CommonProcessors.CollectUniquesProcessor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.{ArrayFactory, ExceptionUtil, Processor}
import org.jetbrains.annotations.{Nls, NonNls, Nullable}
import org.jetbrains.plugins.scala.caches.UserDataHolderDelegator
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
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TermSignature}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent
import org.jetbrains.plugins.scala.util.ScalaPluginUtils

import java.lang.ref.Reference
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Callable, ScheduledFuture, TimeUnit, ConcurrentMap => JConcurrentMap, Future => JFuture}
import java.util.regex.Pattern
import java.util.{Arrays, Set => JSet}
import scala.annotation.{nowarn, tailrec}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.runtime.NonLocalReturnControl
import scala.util.control.Exception.catching
import scala.util.{Failure, Success, Try}

/**
  * Pavel Fatin
  */

package object extensions {

  val Placeholder = "_"

  implicit class PsiMethodExt(private val repr: PsiMethod) extends AnyVal {

    import PsiMethodExt._

    implicit private def project: ProjectContext = repr.getProject

    def isAccessor: Boolean = isParameterless &&
      hasQueryLikeName &&
      repr.getReturnType != PsiType.VOID

    def hasQueryLikeName: Boolean = {
      val name = repr.name

      def startsWith(prefix: String): Boolean =
        name.length > prefix.length && name.startsWith(prefix) && name.charAt(prefix.length).isUpper

      name != "getInstance" && // TODO others?
        AccessorNamePattern.matcher(name).matches() &&
        !startsWith("getAnd") &&
        !startsWith("getOr")
    }

    def parameters: Seq[PsiParameter] =
      repr.getParameterList.getParameters.toSeq

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

    def functionType(implicit scope: ElementScope): Option[ScType] = repr match {
      case fun: ScFunction =>
        fun.`type`().toOption
      case method => // java method
        val returnType = Option(method.getReturnType).map(_.toScType())
        val paramTypes = method.parameters.map(_.getType.toScType())
        returnType.map(FunctionType(_, paramTypes))
    }
  }

  object PsiMethodExt {
    private val AccessorNamePattern = Pattern.compile(
      """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*"""
    )
  }

  implicit class PsiFileExt(private val file: PsiFile) extends AnyVal {

    def charSequence: CharSequence =
      if (file.isValid) viewProvider.getContents
      else file.getText

    def hasScalaPsi: Boolean = viewProvider.hasScalaPsi

    def findScalaFile[F <: ScalaFile : ClassTag]: Option[F] =
      viewProvider.getPsi(ScalaLanguage.INSTANCE).asOptionOf[F]

    def findAnyScalaFile: Option[ScalaFile] =
      findScalaFile[ScalaFile]

    def findScalaLikeFile: Option[PsiFile] =
      if (file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) Option(file) else findAnyScalaFile

    def isScala3File: Boolean =
      file.getLanguage.isKindOf(Scala3Language.INSTANCE) // Scala3Language, WorksheetLanguage3

    def isScala2File: Boolean =
      file.getLanguage == ScalaLanguage.INSTANCE

    def isScalaWorksheet: Boolean = file match {
      case scalaFile: ScalaFile => scalaFile.isWorksheetFile
      case _ => false
    }

    private def viewProvider = file.getViewProvider
  }

  implicit class ViewProviderExt(private val viewProvider: FileViewProvider) extends AnyVal {
    def hasScalaPsi: Boolean = viewProvider.getBaseLanguage.isKindOf(ScalaLanguage.INSTANCE) || viewProvider.getPsi(ScalaLanguage.INSTANCE) != null
  }

  implicit class IterableOnceExt[CC[X] <: collection.IterableOnceOps[X, CC, CC[X]], A](private val value: CC[A]) extends AnyVal {
    def foreachDefined(pf: PartialFunction[A, Unit]): Unit =
      value.foreach(pf.applyOrElse(_, (_: A) => ()))

    def filterByType[T <: AnyRef : ClassTag]: CC[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      value.filter(clazz.isInstance).map(_.asInstanceOf[T])
    }

    def findByType[T <: AnyRef : ClassTag]: Option[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      value.find(clazz.isInstance).asInstanceOf[Option[T]]
    }

    def findFirstBy[T <: AnyRef : ClassTag](predicate: T => Boolean): Option[T] = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      val result = value.find {
        case elem if clazz.isInstance(elem) && predicate(elem.asInstanceOf[T]) => true
        case _ => false
      }
      result.asInstanceOf[Option[T]]
    }

    def zipMapped[B](f: A => B): CC[(A, B)] =
      value.map(item => item -> f(item))
  }

  implicit class IterableExt[CC[X] <: collection.IterableOps[X, CC, CC[X]], A](private val value: CC[A]) extends AnyVal {
    def mapWithIndex[B](f: (A, Int) => B): CC[B] = {
      val builder = value.iterableFactory.newBuilder[B]
      builder.sizeHint(value)
      var i = 0
      for (x <- value) {
        builder += f(x, i)
        i += 1
      }
      builder.result()
    }

    def join[B](separator: B)
               (generator: A => Seq[B]): Seq[B] = {
      val delegate = value.iterator

      @annotation.tailrec
      def join(result: Seq[B],
               intersperseNext: Boolean = false): Seq[B] =
        if (intersperseNext) join(result :+ separator)
        else {
          if (delegate.hasNext) {
            val value = generator(delegate.next())
            join(
              result ++ value,
              delegate.hasNext
            )
          } else result
        }

      join(List.empty[B])
    }

    def join[B](start: B,
                separator: B,
                end: B)
               (generator: A => Seq[B]): Seq[B] =
      start +: join(separator)(generator) :+ end

    // https://github.com/scala/collection-strawman/issues/208
    def intersperse[B >: A](sep: B): Seq[B] = value.iterator.intersperse(sep).toSeq

    def intersperse[B >: A](start: B, sep: B, end: B): Seq[B] = value.iterator.intersperse(start, sep, end).toSeq

    // https://pavelfatin.com/twitter-puddles-and-foldlr
    def foldlr[L, R](l: L, r: R)(f1: (L, A) => L)(f2: (L, A, R) => R): R =
      if (value.isEmpty) r
      else f2(l, value.head, value.tail.foldlr(f1(l, value.head), r)(f1)(f2))

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

    def makeArray[B >: A <: AnyRef](implicit factory: ArrayFactory[B]): Array[B] = mapToArray(_.asInstanceOf[B])

    def mkParenString(implicit ev: A <:< String): String = value.mkString("(", ", ", ")")
  }

  implicit class IterableOfNullablesExt[CC[X] <: collection.IterableOps[X, CC, CC[X]], A <: AnyRef](private val value: CC[A]) extends AnyVal {
    //may return same instance if no element was changed
    def smartMapWithIndex(f: (A, Int) => A): CC[A] = {
      val builder = value.iterableFactory.newBuilder[A]
      builder.sizeHint(value)
      val iterator = value.iterator
      var i = 0
      var updated = false
      while (iterator.hasNext) {
        val next = iterator.next()
        val fNext = f(next, i)
        if (next ne fNext) {
          updated = true
        }
        builder += fNext
        i += 1
      }
      if (updated) builder.result()
      else value
    }

    //may return same instance if no element was changed
    def smartMap(f: A => A): CC[A] = {
      val builder = value.iterableFactory.newBuilder[A]
      builder.sizeHint(value)
      val iterator = value.iterator
      var updated = false
      while (iterator.hasNext) {
        val next = iterator.next()
        val fNext = f(next)
        if (next ne fNext) {
          updated = true
        }
        builder += fNext
      }
      if (updated) builder.result()
      else value
    }
  }

  implicit class ArrayExt[A](val array: Array[A]) extends AnyVal {
    def findByType[T <: AnyRef : ClassTag]: Option[T] = collectFirstByType(identity[T])

    def collectFirstByType[T <: AnyRef : ClassTag, R](f: T => R): Option[R] = {
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

  implicit class ToNullSafe[+A >: Null](private val a: A) extends AnyVal {
    def nullSafe: NullSafe[A] = NullSafe(a)
  }

  implicit class OptionToNullSafe[+A >: Null](private val a: Option[A]) extends AnyVal {
    //to handle Some(null) case and avoid wrapping of intermediate function results
    //in chained map/flatMap calls
    def toNullSafe: NullSafe[A] = NullSafe(a.orNull)
  }

  implicit class ObjectExt[T](private val v: T) extends AnyVal {
    def toOption: Option[T] = Option(v)

    def ifNot(predicate: T => Boolean): Option[T] =
      if (predicate(v)) None else Some(v)

   /** Concise and type-safe version of isInstanceOf / typed pattern.
    *
    * Any dynamic type test is potentially unsafe, because it can be appied to a wrong (expression, Type) combination,
    * either initially, or after a refactoring, making refactorings unsafe.
    *
    * The Scala compiler can detect fruitless type tests, and show a warning for isInstanceOf, and an error for match.
    * However, the warning is not enough, whereas the match is too verbose.
    *
    * Another problem is that incorrect type tests can be formally proven wrong in a very limited number of cases.
    * Instead of replicating that logic in a macro, we can define a more strict version of a dynamic type test,
    * which requires that the type of expression is a supertype of the given type argument.
    * In this way, we can detect more issues statically.
    *
    * The additional restriction limits the applicability of the method, but that's OK for our use cases,
    * because the most common use case is testing whether an instance of PsiElement is a subtype of ...Element.
    *
    * Ideally, pattern matching should only be used with ADTs, where the subtype / supertype relation is
    * verified at compile time (and is just an implementation detail - it's about data constructors, not subtypes),
    * and all the variants are statically known. Although we cannot seal the PsiElement hierarchy, we can at least
    * verify the subtype / supertype relation. So this method is a good match for semi-sum types :)
    */
    @inline def is[T1 <: T : ClassTag]: Boolean = v match { case _: T1 => true; case _ => false }
    @inline def is[T1 <: T : ClassTag, T2 <: T : ClassTag]: Boolean = is[T1] || is[T2]
    @inline def is[T1 <: T : ClassTag, T2 <: T : ClassTag, T3 <: T : ClassTag]: Boolean = is[T1, T2] || is[T3]
    @inline def is[T1 <: T : ClassTag, T2 <: T : ClassTag, T3 <: T : ClassTag, T4 <: T : ClassTag]: Boolean = is[T1, T2, T3] || is[T4]
    @inline def is[T1 <: T : ClassTag, T2 <: T : ClassTag, T3 <: T : ClassTag, T4 <: T : ClassTag, T5 <: T : ClassTag]: Boolean = is[T1, T2, T3, T4] || is[T5]

    // See the `is` method description.
    def asOptionOf[E <: T : ClassTag]: Option[E] = asOptionOfUnsafe[E]

    // For `is` there's "unsafe" `isInstanceOf`, but for `asOption` there's only `match` (which is verbose).
    def asOptionOfUnsafe[E : ClassTag]: Option[E] =
      v match {
        case e: E => Some(e)
        case _    => None
      }
  }

  implicit class ReferenceExt[T](private val target: Reference[T]) extends AnyVal {

    def getOpt: Option[T] = Option(target.get())
  }

  implicit class OptionExt[T](private val option: Option[T]) extends AnyVal {
    def getOrThrow(exception: => Exception): T = option.getOrElse(throw exception)

    // Use for safely checking for null in chained calls
    @inline def safeMap[A](f: T => A): Option[A] = if (option.isEmpty) None else Option(f(option.get))

    def filterByType[S <: AnyRef : ClassTag]: Option[S] = {
      option match {
        case Some(element) if implicitly[ClassTag[S]].runtimeClass.isInstance(element) =>
          option.asInstanceOf[Option[S]]
        case _ => None
      }
    }
  }

  implicit class BooleanExt(private val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if (b) Option(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: => A): Seq[A] = if (b) Seq(a) else Seq.empty

    // looks better withing expressions than { if (???) ??? else ??? } block
    def fold[T](ifTrue: => T, ifFalse: => T): T = if (b) ifTrue else ifFalse

    def toInt: Int = if (b) 1 else 0
  }

  //noinspection ReferenceMustBePrefixed
  implicit class IntArrayExt(private val array: Array[Int]) extends AnyVal {

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

  implicit class StringExt(private val string: String) extends AnyVal {
    def startsWith(ch: Char): Boolean = {
      string.nonEmpty && string.charAt(0) == ch
    }

    def endsWith(ch: Char): Boolean = {
      string.nonEmpty && string.charAt(string.length - 1) == ch
    }

    def parenthesize(needParenthesis: Boolean = true): String =
      if (needParenthesis) s"($string)" else string

    def braced(needBraces: Boolean = true, withNewLines: Boolean = true): String = {
      val nl = if(withNewLines) "\n" else ""
      if (needBraces) s"{$nl$string$nl}" else string
    }

    // TODO: rename to reflect that it's line separator
    def withNormalizedSeparator: String =
      StringUtil.convertLineSeparators(string)

    def toIntOpt: Option[Int] = parseNumeric(string.toInt)

    def toFloatOpt: Option[Float] = parseNumeric(string.toFloat)

    @inline private def parseNumeric[T: Numeric](parse: => T): Option[T] = try Some(parse) catch {
      case _: NumberFormatException => None
    }

    // TODO: remove, and use stripTrailing() (available since JDK 11)
    //  (search for similar methods definitions in project)
    def trimRight: String = StringExt.TrimRightRegex.replaceFirstIn(string, "")

    def shorten(maxLen: Int, restMarker: String = "..."): String = {
      assert(maxLen >= restMarker.length)
      if (string.length <= maxLen) string
      else string.substring(0, maxLen - restMarker.length) + restMarker
    }
  }

  object StringExt {

    private val TrimRightRegex = "\\s+$".r
  }

  implicit class CharSeqExt(private val cs: CharSequence) extends AnyVal {
    private def iterator: Iterator[Char] = new Iterator[Char] {
      var idx = 0

      override def hasNext: Boolean = idx < cs.length()

      override def next(): Char = {
        idx += 1
        cs.charAt(idx - 1)
      }
    }

    def count(pred: Char => Boolean): Int = iterator.count(pred)

    def exists(pred: Char => Boolean): Boolean = iterator.exists(pred)

    def forall(pred: Char => Boolean): Boolean = iterator.forall(pred)

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

    def indexWhere(pred: Char => Boolean): Int = iterator.indexWhere(pred)
  }

  implicit class StringsExt(private val strings: Iterable[String]) extends AnyVal {
    def commaSeparated(model: Model.Val = Model.None): String =
      strings.mkString(model.start, ", ", model.end)
  }

  object Model extends Enumeration {

    @nowarn("msg=shadowing a nested class of a parent is deprecated")
    class Val(val start: String, val end: String) extends super.Val()

    val None = new Val("", "")
    val Parentheses = new Val("(", ")")
    val Braces = new Val("{", "}")
    val SquareBrackets = new Val("[", "]")
  }

  implicit class TextRangeExt(private val target: TextRange) extends AnyVal {
    def expand(delta: Int): TextRange = TextRange.create(target.getStartOffset - delta, target.getEndOffset + delta)
    def shrink(delta: Int): TextRange = TextRange.create(target.getStartOffset + delta, target.getEndOffset - delta)
    def shiftStart(delta: Int): TextRange = TextRange.create(target.getStartOffset + delta, target.getEndOffset)
    def shiftEnd(delta: Int): TextRange = TextRange.create(target.getStartOffset, target.getEndOffset + delta)
    def toTuple: (Int, Int) = (target.getStartOffset, target.getEndOffset)

    /**
     * Checks if two ranges are intersecting while not being subranges of each other.
     *
     * Interlacing:     (   [   )   ]
     * Not interlacing: (   [   ]   )
     */
    def interlaces(rhs: TextRange): Boolean =
      target.intersectsStrict(rhs) && !target.contains(rhs) && !rhs.contains(target)
  }

  object TextRangeExt {
    def unapply(range: TextRange): Option[(Int, Int)] = Some(range.getStartOffset, range.getEndOffset)
  }

  implicit class RangeMarkerExt(private val marker: RangeMarker) extends AnyVal {
    def getTextRange: TextRange = TextRange.create(marker.getStartOffset, marker.getEndOffset)
  }

  implicit class PsiElementExt[E <: PsiElement](private val element: E) extends AnyVal {
    @inline def startOffset: Int = element.getTextRange.getStartOffset

    @inline def endOffset: Int = element.getTextRange.getEndOffset

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
        case e: PsiMethod        => e.functionType(scope)
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

    def parentOfType[Psi <: PsiElement: ClassTag]: Option[Psi] =
      parentOfType(implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]])

    def parentOfType[Psi <: PsiElement](clazz: Class[Psi], strict: Boolean = true): Option[Psi] =
      Option(getParentOfType(element, clazz, strict))

    def parentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getParentOfType(element, classes: _*))

    def nonStrictParentOfType[Psi <: PsiElement: ClassTag]: Option[Psi] =
      nonStrictParentOfType(implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]])

    def nonStrictParentOfType[Psi <: PsiElement](clazz: Class[Psi]): Option[Psi] =
      Option(getNonStrictParentOfType(element, clazz))

    def nonStrictParentOfType(classes: Seq[Class[_ <: PsiElement]]): Option[PsiElement] =
      Option(getNonStrictParentOfType(element, classes: _*))


    def findContextOfType[Psi <: PsiElement](clazz: Class[Psi]): Option[Psi] =
      Option(getContextOfType(element, clazz))

    def elementAt(offset: Int): Option[PsiElement] = Option(element.findElementAt(offset))

    def isAncestorOf(otherElement: PsiElement): Boolean = isAncestor(element, otherElement, true)

    def parents: Iterator[PsiElement] = new ParentsIterator(element)

    def withParents: Iterator[PsiElement] = new ParentsIterator(element, strict = false)

    def parentsInFile: Iterator[PsiElement] = element match {
      case _: PsiFile | _: PsiDirectory => Iterator.empty
      case _ => parents.takeWhile(!_.is[PsiFile])
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

    def nextSiblingOfType[T <: PsiElement: ClassTag]: Option[T] = nextSiblings.findByType[T]

    def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(element)

    def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(element)

    def withNextSiblings: Iterator[PsiElement] = Iterator(element) ++ nextSiblings

    def withPrevSiblings: Iterator[PsiElement] = Iterator(element) ++ prevSiblings

    def prevElement: Option[PsiElement] = element.containingFile.flatMap(_.elementAt(element.startOffset - 1))

    def nextElement: Option[PsiElement] = element.containingFile.flatMap(_.elementAt(element.endOffset))

    def isWhitespace: Boolean = element.isInstanceOf[PsiWhiteSpace]

    def isComment: Boolean = element.isInstanceOf[PsiComment]

    def isWhitespaceOrComment: Boolean = isWhitespace || isComment

    // TODO Scala 2.13: use Iterator.unfold to extract prevElements and nextElements methods
    def prevElementNotWhitespace: Option[PsiElement] = element.prevElement.flatMap(e => if (e.isWhitespace) e.prevElement else Some(e))

    def nextElementNotWhitespace: Option[PsiElement] = element.nextElement.flatMap(e => if (e.isWhitespace) e.nextElement else Some(e))

    def contexts: Iterator[PsiElement] = new ContextsIterator(element)

    def withContexts: Iterator[PsiElement] = new ContextsIterator(element, strict = false)

    def scopes: Iterator[PsiElement] = contexts.filter(ScalaPsiUtil.isScope)

    @inline def elementType: IElementType = element.getNode.getElementType

    def startsFromNewLine(ignoreComments: Boolean = true): Boolean = {
      @tailrec
      def inner(el: PsiElement): Boolean = el match {
        case null => true
        case ws: PsiWhiteSpace =>
          if (ws.textContains('\n')) true
          else inner(PsiTreeUtil.prevLeaf(el))
        case _: PsiComment if ignoreComments =>
          inner(PsiTreeUtil.prevLeaf(el))
        case _ if el.getTextLength == 0 => // empty annotations, modifiers, parse errors, etc...
          inner(PsiTreeUtil.prevLeaf(el))
        case _ => false
      }
      inner(PsiTreeUtil.prevLeaf(element))
    }

    def followedByNewLine(ignoreComments: Boolean = true): Boolean = {
      @tailrec
      def inner(el: PsiElement): Boolean = el match {
        case null => false
        case ws: PsiWhiteSpace =>
          if (ws.textContains('\n')) true
          else inner(PsiTreeUtil.nextLeaf(el))
        case _: PsiComment if ignoreComments =>
          inner(PsiTreeUtil.nextLeaf(el))
        case _ if el.getTextLength == 0 => // empty annotations, modifiers, parse errors, etc...
          inner(PsiTreeUtil.nextLeaf(el))
        case _ => false
      }
      inner(PsiTreeUtil.nextLeaf(element))
    }

    def getPrevSiblingNotWhitespace: PsiElement = {
      var prev: PsiElement = element.getPrevSibling
      while (prev != null && (prev.is[PsiWhiteSpace] ||
        prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) prev = prev.getPrevSibling
      prev
    }

    def prevSiblingNotWhitespace: Option[PsiElement] =
       Option(getPrevSiblingNotWhitespace)

    def getPrevSiblingNotWhitespaceComment: PsiElement = {
      var prev: PsiElement = element.getPrevSibling
      while (prev != null && (prev.is[PsiWhiteSpace] ||
        prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || prev.is[PsiComment]))
        prev = prev.getPrevSibling
      prev
    }

    def prevSiblingNotWhitespaceComment: Option[PsiElement] =
      Option(getPrevSiblingNotWhitespaceComment)

    def getNextSiblingNotWhitespace: PsiElement = {
      var next: PsiElement = element.getNextSibling
      while (next != null && (next.is[PsiWhiteSpace] ||
        next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) next = next.getNextSibling
      next
    }

    def nextSiblingNotWhitespace: Option[PsiElement] =
      Option(getNextSiblingNotWhitespace)

    def nextLeaf: Option[PsiElement] =
      PsiTreeUtil.nextLeaf(element).toOption

    def nextLeafs: Iterator[PsiElement] = new Iterator[PsiElement] {
      private var cur: PsiElement = element
      next()

      override def hasNext: Boolean = cur != null
      override def next(): PsiElement = {
        val result = cur
        cur = PsiTreeUtil.nextLeaf(cur)
        result
      }
    }

    def prevLeaf: Option[PsiElement] =
      PsiTreeUtil.prevLeaf(element).toOption

    def prevLeafs: Iterator[PsiElement] = new Iterator[PsiElement] {
      private var cur: PsiElement = element
      next()

      override def hasNext: Boolean = cur != null
      override def next(): PsiElement = {
        val result = cur
        cur = PsiTreeUtil.prevLeaf(cur)
        result
      }
    }

    final def nextVisibleLeaf: Option[PsiElement] =
      nextVisibleLeaf(skipComments = false)

    @tailrec
    final def nextVisibleLeaf(skipComments: Boolean): Option[PsiElement] =
      PsiTreeUtil.nextVisibleLeaf(element) match {
        case comment: PsiComment if skipComments => comment.nextVisibleLeaf(skipComments)
        case next => next.toOption
      }

    final def prevVisibleLeaf: Option[PsiElement] =
      prevVisibleLeaf(skipComments = false)

    @tailrec
    final def prevVisibleLeaf(skipComments: Boolean): Option[PsiElement] =
      PsiTreeUtil.prevVisibleLeaf(element) match {
        case comment: PsiComment if skipComments => comment.prevVisibleLeaf(skipComments)
        case next => next.toOption
      }

    def firstLeaf: PsiElement =
      PsiTreeUtil.getDeepestFirst(element)

    def lastLeaf: PsiElement =
      PsiTreeUtil.getDeepestLast(element)

    def getFirstChildNotWhitespace: PsiElement =
      element.getFirstChild match {
        case ws: PsiWhiteSpace => ws.getNextSiblingNotWhitespace
        case child => child
      }

    def firstChildNotWhitespace: Option[PsiElement] =
      Option(getFirstChildNotWhitespace)

    def getFirstChildNotWhitespaceComment: PsiElement =
      element.getFirstChild match {
        case ws@(_: PsiWhiteSpace | _: PsiComment) => ws.getNextSiblingNotWhitespaceComment
        case child => child
      }

    def firstChildNotWhitespaceComment: Option[PsiElement] =
      Option(getFirstChildNotWhitespaceComment)

    def getNextSiblingNotWhitespaceComment: PsiElement = {
      var next: PsiElement = element.getNextSibling
      while (next != null && (next.is[PsiWhiteSpace] ||
        next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || next.is[PsiComment]))
        next = next.getNextSibling
      next
    }

    def nextSiblingNotWhitespaceComment: Option[PsiElement] =
      Option(getNextSiblingNotWhitespaceComment)

    /** skips empty annotations, modifiers, etc.. */
    def getPrevNonEmptyLeaf: PsiElement = {
      var prev = PsiTreeUtil.prevLeaf(element)
      while (prev != null && prev.getTextLength == 0)
        prev = PsiTreeUtil.prevLeaf(prev)
      prev
    }

    def getNextNonWhitespaceAndNonEmptyLeaf: PsiElement = {
      var next = PsiTreeUtil.nextLeaf(element)
      while (next != null && (next.getTextLength == 0 || next.is[PsiWhiteSpace]))
        next = PsiTreeUtil.nextLeaf(next)
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

    def startsWithToken(token: IElementType): Boolean = {
      PsiTreeUtil.firstChild(element).elementType == token
    }

    def hasParseError: Boolean = elements.exists(_.is[PsiErrorElement])

    def isInScala3File: Boolean = {
      val file = element.getContainingFile
      file != null && file.isScala3File
    }
  }

  implicit class PsiTypeExt(@Nullable val `type`: PsiType) extends AnyVal {
    def toScType(paramTopLevel: Boolean = false,
                 treatJavaObjectAsAny: Boolean = true)
                (implicit project: ProjectContext): ScType =
      project.typeSystem.toScType(`type`, treatJavaObjectAsAny, paramTopLevel)
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

    def qualifiedNameOpt: Option[String] = member match {
      case c: PsiClass => c.qualifiedName.toOption
      case _ =>
        for {
          cClass      <- containingClass.toOption
          classQName  <- cClass.qualifiedName.toOption
          name        <- names.headOption
        } yield {
          s"$classQName.$name"
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
        case _ => ArraySeq.unsafeWrapArray(clazz.getConstructors)
      }

    def isEffectivelyFinal: Boolean = clazz match {
      case scClass: ScClass => scClass.hasFinalModifier
      case _: ScObject | _: ScNewTemplateDefinition => true
      case synth: ScSyntheticClass if !Seq("AnyRef", "AnyVal").contains(synth.className) => true //wrappers for value types
      case _ =>
        //noinspection ScalaWrongPlatformMethodsUsage
        clazz.hasModifierProperty(PsiModifier.FINAL)
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

    def processWrappersForSignature(signature: TermSignature, isStatic: Boolean, isInterface: Boolean)
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

      if (!signature.namedElement.isValid)
        return

      signature.namedElement match {
        case fun: ScFunction if !fun.isConstructor =>
          val wrappers = fun.getFunctionWrappers(isStatic, isAbstract = fun.isAbstractMember, concreteClassFor(fun))
          wrappers.foreach(processMethod)
          wrappers.foreach(w => processName(w.name))
        case method: PsiMethod if !method.isConstructor =>
          if (isStatic) {
            if (method.containingClass != null && !method.containingClass.isJavaLangObject) {
              processMethod(StaticPsiMethodWrapper.getWrapper(method, clazz))
              processName(method.getName)
            }
          }
          else {
            processMethod(method)
            processName(method.getName)
          }
        case t: ScTypedDefinition if t.isVal || t.isVar ||
          (t.is[ScClassParameter] && t.asInstanceOf[ScClassParameter].isCaseClassVal) =>

          PsiTypedDefinitionWrapper.processWrappersFor(t, concreteClassFor(t), signature.name, isStatic, isInterface, processMethod, processName)
        case _ =>
      }
    }

    def isJavaLangObject: Boolean =
      clazz.qualifiedName == "java.lang.Object"

    def namedElements: Seq[PsiNamedElement] = {
      clazz match {
        case td: ScTemplateDefinition =>
          td.membersWithSynthetic.flatMap {
            case holder: ScDeclaredElementsHolder => holder.declaredElements
            case named: ScNamedElement => Seq(named)
            case _ => Seq.empty
          }
        case _ => ArraySeq.unsafeWrapArray(clazz.getFields ++ clazz.getMethods)
      }
    }

    def sameOrInheritor(other: PsiClass): Boolean = areClassesEquivalent(clazz, other) || isInheritorDeep(clazz, other)

    def superTypes: Seq[ScType] = clazz match {
      case tdef: ScTemplateDefinition => tdef.superTypes
      case _                          => clazz.getSuperTypes.map(_.toScType()(clazz)).toSeq
    }
  }

//  implicit class NavigationItemExt(private val item: NavigationItem) extends AnyVal {
//    def name: String = item match {
//      case scItem: ScNamedElement => scItem.name
//      case _ => item.getName
//    }
//  }

  implicit class PsiNamedElementExt(private val named: PsiNamedElement) extends AnyVal {
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

    def containingClassOfNameContext: Option[PsiClass] = named.nameContext match {
      case m: PsiMember => Option(m.containingClass)
      case _            => None
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

  implicit class ASTNodeExt(private val node: ASTNode) extends AnyVal {
    def treeNextNodes: Iterator[ASTNode] = new ASTNodeTreeNextIterator(node)
    def withTreeNextNodes: Iterator[ASTNode] = Iterator(node) ++ new ASTNodeTreeNextIterator(node)
    def treePrevNodes: Iterator[ASTNode] = new ASTNodeTreePrevIterator(node)
    def withTreePrevNodes: Iterator[ASTNode] = Iterator(node) ++ new ASTNodeTreePrevIterator(node)

    def hasElementType(elementType: IElementType): Boolean =
      node.nullSafe.exists(_.getElementType == elementType)

    def isWhitespaceOrComment: Boolean = {
      node != null && PsiImplUtil.isWhitespaceOrComment(node)
    }
  }

  implicit class PipedObject[T](private val value: T) extends AnyVal {
    def |>[R](f: T => R): R = f(value)
  }

  implicit class DisposableExt[T <: Disposable](private val target: T) extends AnyVal {
    def delegateUserDataHolder: UserDataHolderEx =
      UserDataHolderDelegator.userDataHolderFor(target)
  }

  implicit class IteratorExt[A](private val delegate: Iterator[A]) extends AnyVal {
    def findByType[T: ClassTag]: Option[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.find(aClass.isInstance).asInstanceOf[Option[T]]
    }

    def findByType[T1 <: A: ClassTag, T2 <: A: ClassTag]: Option[A] = {
      delegate.find(_.is[T1, T2])
    }

    def filterByType[T <: AnyRef : ClassTag]: Iterator[T] = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.filter(aClass.isInstance).asInstanceOf[Iterator[T]]
    }

    def containsInstanceOf[T <: AnyRef : ClassTag]: Boolean = {
      val aClass = implicitly[ClassTag[T]].runtimeClass
      delegate.exists(aClass.isInstance)
    }

    def headOption: Option[A] = {
      if (delegate.hasNext) Some(delegate.next())
      else None
    }

    def lastOption: Option[A] = {
      if (!delegate.hasNext)
        return None

      while (true) {
        val value = delegate.next()
        if (!delegate.hasNext)
          return Some(value)
      }
      throw new NotImplementedError
    }

    // https://github.com/scala/collection-strawman/issues/208
    def intersperse[B >: A](sep: B): Iterator[B] = new Iterator[B] {
      private var intersperseNext = false

      override def hasNext: Boolean = intersperseNext || delegate.hasNext

      override def next(): B = {
        val element = if (intersperseNext) sep else delegate.next()
        intersperseNext = !intersperseNext && delegate.hasNext
        element
      }
    }

    def intersperse[B >: A](start: B, sep: B, end: B): Iterator[B] =
      Iterator(start) ++ delegate.intersperse(sep) ++ Iterator(end)
  }

  implicit class ConcurrentMapExt[K, V](private val map: JConcurrentMap[K, V]) extends AnyVal {

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

  implicit class ThrowableExt(private val ex: Throwable) extends AnyVal {

    def stackTraceText: String = ExceptionUtil.getThrowableText(ex).replace("\r", "")
  }

  import scala.language.implicitConversions

  implicit def toIdeaFunction[A, B](f: Function[A, B]): com.intellij.util.Function[A, B] = (param: A) => f(param)

  implicit def toProcessor[T](action: T => Boolean): Processor[T] = (t: T) => action(t)

  implicit def toComputable[T](action: => T): Computable[T] = () => action

  implicit def toCallable[T](action: => T): Callable[T] = () => action

  def startCommand(@Nls commandName: String = null)
                  (body: => Unit)
                  (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      () => body,
      commandName,
      null
    )

  def executeWriteActionCommand(@Nls commandName: String = "",
                                policy: UndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT)
                               (body: => Unit)
                               (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      (() => inWriteAction(body)) : Runnable,
      commandName,
      null,
      policy
    )

  def executeWriteActionCommand(runnable: Runnable,
                                @Nls commandName: String,
                                policy: UndoConfirmationPolicy)
                               (implicit project: Project): Unit =
    CommandProcessor.getInstance().executeCommand(
      project,
      (() => WriteCommandAction.runWriteCommandAction(project, runnable)): Runnable,
      commandName,
      null,
      policy
    )

  def executeUndoTransparentAction(body: => Any): Unit =
    CommandProcessor.getInstance().runUndoTransparentAction(() => body)

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
      ProgressManager.getInstance().executeNonCancelableSection { () =>
        ref.set(ApplicationUtil.tryRunReadAction(body))
      }
      ref.get
    } catch {
      case _: ProcessCanceledException => default
    }
  }

  def executeOnPooledThread[T](body: => T): JFuture[T] =
    ApplicationManager.getApplication.executeOnPooledThread(body)

  def scheduleOnPooledThread[T](time: Long, unit: TimeUnit)(body: => T): ScheduledFuture[T] =
    AppExecutorUtil.getAppScheduledExecutorService.schedule(body, time, unit)

  def schedulePeriodicTask(delay: Long, unit: TimeUnit, parentDisposable: Disposable)(body: => Unit): Unit = {
    val task = AppExecutorUtil.getAppScheduledExecutorService.scheduleWithFixedDelay(() => body, delay, delay, unit)
    invokeOnDispose(parentDisposable) {
      task.cancel(true)
    }
  }

  def withProgressSynchronously[T](@Nls title: String)(body: => T): T = {
    withProgressSynchronouslyTry[T](title)(_ => body) match {
      case Success(result) => result
      case Failure(exception) => throw exception
    }
  }

  def withProgressSynchronouslyTry[T](@Nls title: String, canBeCanceled: Boolean = false)(body: ProgressManager => T): Try[T] = {
    val manager = ProgressManager.getInstance
    catching(classOf[Exception]).withTry {
      manager.runProcessWithProgressSynchronously(new ThrowableComputable[T, Exception] {
        override def compute: T = body(manager)
      }, title, canBeCanceled, null)
    }
  }

  def postponeFormattingWithin[T](project: Project)(body: => T): T =
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(body)

  def withDisabledPostprocessFormatting[T](project: Project)(body: => T): T =
    PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(body)

  // Make sure to handle possible exceptions
  def invokeInFuture[T](body: => T): Future[T] = {
    val promise = Promise[T]()
    invokeLater(promise.complete(Try(body)))
    promise.future
  }

  def invokeLater[T](body: => T): Unit =
    ApplicationManager.getApplication.invokeLater(() => body)

  def invokeLater[T](modalityState: ModalityState)(body: => T): Unit =
    ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = body
    }, modalityState)

  def invokeAndWait[T](body: => T): T = {
    val result = new AtomicReference[T]()
    preservingControlFlow {
      ApplicationManager.getApplication.invokeAndWait(() => result.set(body))
    }
    result.get()
  }

  def invokeAndWait[T](modalityState: ModalityState)(body: => T): Unit =
    preservingControlFlow {
      ApplicationManager.getApplication.invokeAndWait((() => body): Runnable, modalityState)
    }

  def invokeLaterInTransaction(disposable: Disposable)(body: => Unit): Unit = {
    TransactionGuard.getInstance().submitTransactionLater(disposable, () => body): @nowarn("cat=deprecation")
  }

  def invokeAndWaitInTransaction(body: => Unit): Unit = {
    TransactionGuard.getInstance().submitTransactionAndWait(() => body): @nowarn("cat=deprecation")
  }

  def registerDynamicPluginListener(listener: DynamicPluginListener, parentDisposable: Disposable): Unit = {
    val connection = ApplicationManager.getApplication.getMessageBus.connect(parentDisposable)
    connection.subscribe(DynamicPluginListener.TOPIC, listener)
  }

  def invokeOnDispose(parentDisposable: Disposable)(body: => Unit): Unit =
    Disposer.register(parentDisposable, () => body)

  private def preservingControlFlow(body: => Unit): Unit =
    try {
      body
    } catch {
      case e: InvocationTargetException => e.getTargetException match {
        case control: NonLocalReturnControl[_] => throw control
        case _ => throw e
      }
    }

  /** Create a PartialFunction from a sequence of cases. Workaround for pattern matcher bug */
  def pf[A, B](cases: PartialFunction[A, B]*): PartialFunction[A, B] = new PartialFunction[A, B] {
    override def isDefinedAt(x: A): Boolean = cases.exists(_.isDefinedAt(x))

    override def apply(v1: A): B = {
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

    def paramType(extractVarargComponent: Boolean = true, treatJavaObjectAsAny: Boolean = true): ScType = param match {
      case parameter: FakePsiParameter => parameter.parameter.paramType
      case parameter: ScParameter => parameter.`type`().getOrAny
      case _ =>
        val paramType = param.getType match {
          case arrayType: PsiArrayType if extractVarargComponent && param.isVarArgs =>
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

    def stubOrPsiChildren: Array[PsiElement] = stubOrPsiChildren(TokenSet.ANY, PsiElement.ARRAY_FACTORY)

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

    def lastChildStub: Option[PsiElement] = {
      val children = stubOrPsiChildren
      val size = children.length
      if (size == 0) None
      else Some(children(size - 1))
    }

    def withNextStubOrAstContextSiblings: Iterator[PsiElement] = element.sameElementInContext match {
      case st: StubBasedPsiElementBase[_] if st.getStub != null =>
        val contextSiblings = st.getContext.stubOrPsiChildren.iterator
        contextSiblings.dropWhile(_ != st)
      case elem =>
        elem.withNextSiblings
    }

    def hasOnlyStub: Boolean = element match {
      case st: StubBasedPsiElementBase[_] => st.getStub != null
      case _ => false
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

  def cachify[A1, R](f: A1 => R): A1 => R = {
    val cache = mutable.HashMap.empty[A1, R]
    (arg: A1) => cache.getOrElse(arg, f(arg))
  }

  //noinspection TypeAnnotation
  final class CollectUniquesProcessorEx[T] extends CollectUniquesProcessor[T] {
    def results =
      getResults.asScala.toSet

    override def getResults =
      super.getResults.asInstanceOf[JSet[T]]
  }

  val ChildOf: Parent.type = Parent

  implicit final class LoggerExt(private val logger: Logger) extends AnyVal {

    def debugSafe(@NonNls message: => String): Unit =
      if (logger.isDebugEnabled) {
        logger.debug(message)
      }

    def debugSafe(@NonNls message: => String, ex: Throwable): Unit =
      if (logger.isDebugEnabled) {
        logger.debug(message, ex)
      }

    def traceSafe(@NonNls message: => String): Unit =
      if (logger.isTraceEnabled)
        logger.trace(message)

    def traceWithDebugInDev(@NonNls message: => String): Unit =
      if (logger.isTraceEnabled)
        logger.trace(message)
      else if (ApplicationManager.getApplication.isUnitTestMode || ScalaPluginUtils.isRunningFromSources)
        logger.debugSafe(message)

    def warnWithErrorInTests(@NonNls message: => String): Unit =
      if (ApplicationManager.getApplication.isUnitTestMode)
        logger.error(message)
      else
        logger.warn(message)

    def warnWithErrorInTests(@NonNls message: => String, cause: Throwable): Unit =
      if (ApplicationManager.getApplication.isUnitTestMode)
        logger.error(message, cause)
      else
        logger.warn(message, cause)
  }

  implicit class HighlightInfoExt(private val info: HighlightInfo) extends AnyVal {
    def range: TextRange = TextRange.create(info.startOffset, info.endOffset)
  }

  implicit class PathExt(private val path: Path) extends AnyVal {
    def parents: Iterator[Path] =
      withParents.drop(1)
    def withParents: Iterator[Path] =
      Iterator.iterate(path)(_.getParent).takeWhile(_ != null)
  }

  object executionContext {
    private val appExecutorService = AppExecutorUtil.getAppExecutorService
    implicit val appExecutionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(appExecutorService)
  }
}
