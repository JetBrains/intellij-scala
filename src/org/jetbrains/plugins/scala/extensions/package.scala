package org.jetbrains.plugins.scala

import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.{Result, ApplicationManager}
import extensions.implementation._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.openapi.project.Project
import com.intellij.psi._
import scala.util.matching.Regex
import javax.swing.SwingUtilities
import scala.runtime.NonLocalReturnControl
import java.lang.reflect.InvocationTargetException
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import java.io.Closeable
import com.intellij.openapi.command.{WriteCommandAction, CommandProcessor}
import org.jetbrains.annotations.NotNull
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProgressIndicator, Task, ProgressManager}
import com.intellij.openapi.progress.util.ProgressWindow

/**
  * Pavel Fatin
  */

package object extensions {
  implicit def toPsiMethodExt(method: PsiMethod) = new PsiMethodExt(method)

  implicit def toTraversableExt[CC[X] <: Traversable[X], A](t: CC[A]): TraversableExt[CC, A] =
    new TraversableExt[CC, A](t)

  implicit def toSeqExt[CC[X] <: Seq[X], A](t: CC[A]): SeqExt[CC, A] =
    new SeqExt[CC, A](t)

  implicit def toIterableExt[CC[X] <: Seq[X], A](t: CC[A]): IterableExt[CC, A] =
    new IterableExt[CC, A](t)

  implicit def toObjectExt[T](o: T) = new ObjectExt[T](o)

  implicit def toBooleanExt[T](b: Boolean) = new BooleanExt(b)

  implicit def toStringExt[T](s: String) = new StringExt(s)

  implicit def toPsiElementExt(e: PsiElement) = new PsiElementExt {
    override def repr = e
  }
  
  implicit def toPsiClassExt(e: PsiClass) = new PsiClassExt(e)

  implicit def toPsiNamedElementExt(e: PsiNamedElement) = new PsiNamedElementExt(e)

  implicit def toPsiMemberExt(e: PsiMember) = new PsiMemberExt(e)

  implicit def toPsiModifierListOwnerExt(e: PsiModifierListOwner) = new PsiModifierListOwnerExt(e)

  implicit def toPipedObject[T](value: T) = new PipedObject[T](value)

  implicit def toRichIterator[A](it: Iterator[A]) = new IteratorExt[A](it)

  implicit def toIdeaFunction[A, B](f: Function[A, B]) = new com.intellij.util.Function[A, B] {
    def fun(param: A) = f(param)
  }

  implicit def regexToRichRegex(r: Regex) = new RegexExt(r)
  
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
  }

  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }
}