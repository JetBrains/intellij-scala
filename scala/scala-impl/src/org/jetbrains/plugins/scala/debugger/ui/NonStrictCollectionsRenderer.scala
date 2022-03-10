package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.impl.{DebuggerUtilsAsync, PositionUtil}
import com.intellij.debugger.ui.impl.watch.{ValueDescriptorImpl, WatchItemDescriptor}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptor, ValueDescriptor}
import com.intellij.debugger.ui.tree.render._
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiExpression}
import com.intellij.util.IncorrectOperationException
import com.sun.jdi._
import com.sun.tools.jdi.ObjectReferenceImpl
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.NonStrictCollectionsRenderer._

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import scala.reflect.NameTransformer

/**
 * User: Dmitry Naydanov
 * Date: 9/3/12
 */
class NonStrictCollectionsRenderer extends ClassRenderer {

  import ScalaClassRenderer._

  def getStartIndex: Int = ScalaDebuggerSettings.getInstance().COLLECTION_START_INDEX.intValue()

  def getEndIndex: Int = ScalaDebuggerSettings.getInstance().COLLECTION_END_INDEX.intValue()

  override def getUniqueId = "NonStrictCollectionsRenderer"

  override def getName: String = ScalaBundle.message("scala.streams.as.collections")

  def mustNotExpandStreams: Boolean = ScalaDebuggerSettings.getInstance().DO_NOT_DISPLAY_STREAMS

  private def invokeLengthMethodByName(objectRef: ObjectReference, methodName: String, signature: Char,
                                       context: EvaluationContext) = {
    val suitableMethods = objectRef.referenceType().methodsByName(methodName, "()" + signature)
    if (suitableMethods.size() > 0) {
      invokeEmptyArgsMethod(objectRef, suitableMethods get 0, context)
    } else {
      MethodNotFound()
    }
  }

  private def tryToGetSize(objectRef: ObjectReference, context: EvaluationContext): SimpleMethodInvocationResult[_] = {
    @inline def invoke(name: String) = invokeLengthMethodByName(objectRef, name, 'I', context)

    try {
      if (!evaluateHasDefiniteSize(objectRef, context) || isStreamView(objectRef.referenceType())) return Success[String]("?")
    } catch {
      case e: EvaluateException => return Fail(e)
    }

    invoke("size") match {
      case result@Success(_) => result
      case a => a
    }
  }

  def isApplicableFor(tpe: Type): CompletableFuture[java.lang.Boolean] = tpe match {
    case tpe: ReferenceType if !mustNotExpandStreams =>
      orAsync(isLazyAsync(tpe), isViewAsync(tpe))
    case _ => completedFuture(false)
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, evaluationContext: EvaluationContext): Unit = {
    def invokeEmptyArgsMethod(obj: ObjectReference, actualRefType: ReferenceType, methodName: String): Value = {
      val suitableMethods = actualRefType methodsByName methodName
      if (suitableMethods.size() == 0) return null

      try {
        evaluationContext.getDebugProcess.invokeMethod(evaluationContext, obj, suitableMethods get 0, EMPTY_ARGS)
      }
      catch {
        case (_: EvaluateException | _: InvocationException | _: InvalidTypeException |
              _: IncompatibleThreadStateException | _: ClassNotLoadedException) => null
      }
    }

    @inline def getTail(objRef: ObjectReference, actualRefType: ReferenceType) =
      invokeEmptyArgsMethod(objRef, actualRefType, "tail")

    @inline def getHead(objRef: ObjectReference, actualRefType: ReferenceType) =
      invokeEmptyArgsMethod(objRef, actualRefType, "head")

    @inline def getAll(objRef: ObjectReference, actualType: ReferenceType) =
      (getHead(objRef, actualType), getTail(objRef, actualType))

    val myChildren = new util.ArrayList[DebuggerTreeNode]()

    def returnChildren(): Unit = {
      builder.setChildren(myChildren)
    }

    value match {
      case objectRef: ObjectReference if evaluateNonEmpty(objectRef, evaluationContext) =>
        var currentTail = objectRef

        for (i <- 0 until getStartIndex) {
          getTail(currentTail, currentTail.referenceType()) match {
            case newTail: ObjectReference => currentTail = newTail
            case _ => return //ourCollection.size < startIndex
          }
        }

        var indexCount = getStartIndex
        for (i <- getStartIndex to getEndIndex) {
          getAll(currentTail, currentTail.referenceType()) match {
            case (newHead: ObjectReference, newTail: ObjectReference) =>
              val newNode = builder.getNodeManager.createNode(
                new CollectionElementNodeDescriptor(indexCount.toString, evaluationContext.getProject, newHead), evaluationContext)
              myChildren add newNode
              currentTail = newTail
              indexCount += 1
            case _ => returnChildren(); return
          }
        }

      case _ =>
    }

    returnChildren()
  }

  override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiExpression = {
    node.getDescriptor match {
      case watch: WatchItemDescriptor =>
        JavaPsiFacade.getInstance(node.getProject).getElementFactory.createExpressionFromText(watch.calcValueName(), null)
      case collectionItem: CollectionElementNodeDescriptor => collectionItem.getDescriptorEvaluation(context)
      case _ => null
    }

  }

  override def isExpandableAsync(value: Value,
                                 evaluationContext: EvaluationContext,
                                 parentDescriptor: NodeDescriptor): CompletableFuture[java.lang.Boolean] = {
    val isExpandable = value match {
      case objectRef: ObjectReferenceImpl => evaluateNonEmpty(objectRef, evaluationContext)
      case _ => false
    }
    CompletableFuture.completedFuture(isExpandable)
  }

  private def isViewAsync(tpe: Type): CompletableFuture[Boolean] =
    instanceOfAsync(tpe, viewClassName, viewClassName_2_13)

  private def isLazyAsync(tpe: Type): CompletableFuture[Boolean] =
    instanceOfAsync(tpe, streamClassName, lazyList_2_13)

  private def isStreamView(tpe: Type): Boolean = instanceOf(tpe, streamViewClassName)

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, listener: DescriptorLabelListener): String = {
    val stringBuilder = new StringBuilder()

    descriptor.getValue match {
      case obj: ObjectReference =>
        val tpe = obj.referenceType()
        val sizeString = " size = " + (tryToGetSize(obj, context) match {
          case Success(value: Int) => value
          case _ => "?"
        })

        stringBuilder append (if (tpe != null) transformName(tpe.name) + sizeString else "{...}")
      case _ => stringBuilder append "{...}"
    }

    val label = stringBuilder.toString
    label
  }
}

object NonStrictCollectionsRenderer {
  private val EMPTY_ARGS = util.Collections.unmodifiableList(new util.ArrayList[Value]())

  //it considers only part of cases so it is not intended to be used outside 
  private def invokeEmptyArgsMethod(obj: ObjectReference, method: Method, context: EvaluationContext): SimpleMethodInvocationResult[_] = {
    try {
      context.getDebugProcess.invokeMethod(context, obj, method, EMPTY_ARGS) match {
        case intValue: IntegerValue => Success[Int](intValue.intValue())
        case boolValue: BooleanValue => Success[Boolean](boolValue.booleanValue())
        case objValue: ObjectReference => Success[Value](objValue)
        case _ => MethodNotFound()
      }
    }
    catch {
      case e@(_: EvaluateException | _: InvocationException | _: InvalidTypeException |
              _: IncompatibleThreadStateException | _: ClassNotLoadedException) => Fail[Throwable](e)
    }
  }

  private class SimpleMethodInvocationResult[R]
  private case class MethodNotFound() extends SimpleMethodInvocationResult[Nothing]
  private case class Success[R](value: R) extends SimpleMethodInvocationResult[R]
  private case class Fail[E <: Throwable](exc: E) extends SimpleMethodInvocationResult[E]

  private class CollectionElementNodeDescriptor(name: String, project: Project, value: Value) extends ValueDescriptorImpl(project, value) {
    override def calcValue(evaluationContext: EvaluationContextImpl): Value = value

    override def getDescriptorEvaluation(context: DebuggerContext): PsiExpression = {
      try {
        JavaPsiFacade.getInstance(project).getElementFactory.createExpressionFromText(name, PositionUtil getContextElement context)
      }
      catch {
        case _: IncorrectOperationException => null
      }
    }

    override def getName: String = name
  }

  implicit def toCFJBoolean(f: CompletableFuture[Boolean]): CompletableFuture[java.lang.Boolean] =
    f.asInstanceOf[CompletableFuture[java.lang.Boolean]]

  implicit def toCFBoolean(f: CompletableFuture[java.lang.Boolean]): CompletableFuture[Boolean] =
    f.asInstanceOf[CompletableFuture[Boolean]]

  def instanceOf(tp: Type, baseClassNames: String*): Boolean =
    baseClassNames.exists(DebuggerUtils.instanceOf(tp, _))

  def instanceOfAsync(tp: Type, baseClassNames: String*): CompletableFuture[Boolean] = {
    val futures = baseClassNames.map(DebuggerUtilsAsync.instanceOf(tp, _).thenApply[Boolean](_.booleanValue()))
    forallAsync(futures: _*)
  }

  def andAsync(f1: CompletableFuture[Boolean], f2: CompletableFuture[Boolean]): CompletableFuture[Boolean] =
    f1.thenCombine[Boolean, Boolean](f2, _ && _)

  def orAsync(f1: CompletableFuture[Boolean], f2: CompletableFuture[Boolean]): CompletableFuture[Boolean] =
    f1.thenCombine[Boolean, Boolean](f2, _ || _)

  def forallAsync(futures: CompletableFuture[Boolean]*): CompletableFuture[Boolean] =
    futures.reduce(andAsync)

  private val collectionClassName = "scala.collection.Iterable"
  private val streamClassName = "scala.collection.immutable.Stream"
  private val streamViewClassName = "scala.collection.immutable.StreamView"
  private val viewClassName = "scala.collection.IterableView"
  private val iteratorClassName = "scala.collection.Iterator"

  private val viewClassName_2_13 = "scala.collection.View"
  private val lazyList_2_13 = "scala.collection.immutable.LazyList"

  private def size(value: Value, evaluationContext: EvaluationContext): Int =
    ScalaClassRenderer.evaluateSize(value.asInstanceOf[ObjectReference], evaluationContext)

  private def checkNotCollectionOfKindAsync(tp: Type, shortNames: String*)(baseClassNames: String*): CompletableFuture[Boolean] =
    if (shortNames.exists(tp.name().contains(_))) completedFuture(false)
    else instanceOfAsync(tp, baseClassNames: _*).thenApply(!_)

  private[ui] def notViewAsync(tp: Type): CompletableFuture[Boolean] =
    checkNotCollectionOfKindAsync(tp, "View")(viewClassName, viewClassName_2_13)

  private[ui] def notStreamAsync(tp: Type): CompletableFuture[Boolean] =
    checkNotCollectionOfKindAsync(tp, "Stream", "LazyList")(streamClassName, lazyList_2_13)

  /**
   * util method for collection displaying in debugger
   *
   * @param name name encoded for jvm (for example, scala.collection.immutable.$colon$colon)
   * @return decoded nonqualified part (:: in example)
   */
  def transformName(name: String): String = getNonQualifiedName(NameTransformer decode name)

  private def getNonQualifiedName(fullName: String): String = {
    val index =
      if (fullName endsWith "`") fullName.substring(0, fullName.length - 1).lastIndexOf('`')
      else fullName.lastIndexOf('.')
    "\"" + fullName.substring(index + 1) + "\""
  }
}
