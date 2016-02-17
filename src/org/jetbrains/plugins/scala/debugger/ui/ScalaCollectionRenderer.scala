package org.jetbrains.plugins.scala.debugger.ui

import java.util

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, TypeEvaluator}
import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluateException, EvaluationContext, TextWithImportsImpl}
import com.intellij.debugger.engine.{DebugProcessImpl, DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render._
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptor, NodeManager, ValueDescriptor}
import com.intellij.debugger.{DebuggerBundle, DebuggerContext}
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.psi.{PsiElement, PsiManager}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaDuplexEvaluator, ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaThisEvaluator}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.ScalaCollectionRenderer._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.JavaConverters._
import scala.reflect.NameTransformer

/**
 * @author Nikolay.Tropin
 */

class ScalaCollectionRenderer extends CompoundReferenceRenderer(NodeRendererSettings.getInstance(), "Scala collection", sizeLabelRenderer, ScalaToArrayRenderer) {

  setClassName(collectionClassName)


  override def isApplicable(tp: Type): Boolean = {
    super.isApplicable(tp) && notStream(tp) && notView(tp)
  }

  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().FRIENDLY_COLLECTION_DISPLAY_ENABLED

  private def notView(tp: Type): Boolean = !DebuggerUtils.instanceOf(tp, viewClassName)

  private def notStream(tp: Type): Boolean = !DebuggerUtils.instanceOf(tp, streamClassName)
}

object ScalaCollectionRenderer {
  implicit class ToExpressionEvaluator(val e: Evaluator) extends AnyVal {
    def exprEval = new ExpressionEvaluatorImpl(e)
  }

  private val hasDefiniteSizeEval = new ScalaMethodEvaluator(new ScalaThisEvaluator(), "hasDefiniteSize", JVMNameUtil.getJVMRawText("()Z"), Nil)
  private val nonEmptyEval = new ScalaMethodEvaluator(new ScalaThisEvaluator(), "nonEmpty", JVMNameUtil.getJVMRawText("()Z"), Nil)
  private val sizeEval = new ScalaMethodEvaluator(new ScalaThisEvaluator(), "size", JVMNameUtil.getJVMRawText("()I"), Nil)

  val collectionClassName = "scala.collection.GenIterable"
  val streamClassName = "scala.collection.immutable.Stream"
  val streamViewClassName = "scala.collection.immutable.StreamView"
  val viewClassName = "scala.collection.IterableView"
  val iteratorClassName = "scala.collection.Iterator"

  val sizeLabelRenderer = createSizeLabelRenderer()

  def hasDefiniteSize(value: Value, evaluationContext: EvaluationContext) = {
    value.`type`() match {
      case ct: ClassType if ct.name.startsWith("scala.collection") &&
        !DebuggerUtils.instanceOf(ct, streamClassName) && !DebuggerUtils.instanceOf(ct, iteratorClassName) => true
      case _ => evaluateBoolean(value, evaluationContext, hasDefiniteSizeEval)
    }
  }

  def nonEmpty(value: Value, evaluationContext: EvaluationContext) = {
    value.`type`() match {
      case ct: ClassType if ct.name.toLowerCase.contains("empty") || ct.name.contains("Nil") => false
      case _ => evaluateBoolean(value, evaluationContext, nonEmptyEval)
    }
  }

  def size(value: Value, evaluationContext: EvaluationContext) = evaluateInt(value, evaluationContext, sizeEval)

  /**
   * util method for collection displaying in debugger
    *
    * @param name name encoded for jvm (for example, scala.collection.immutable.$colon$colon)
   * @return decoded nonqualified part (:: in example)
   */
  def transformName(name: String) = getNonQualifiedName(NameTransformer decode name)

  private def getNonQualifiedName(fullName: String): String = {
    val index =
      if (fullName endsWith "`") fullName.substring(0, fullName.length - 1).lastIndexOf('`')
      else fullName.lastIndexOf('.')
    "\"" + fullName.substring(index + 1) + "\""
  }

  private def createSizeLabelRenderer(): LabelRenderer = {
    val expressionText = "size()"
    val sizePrefix = " size = "
    val labelRenderer: LabelRenderer = new LabelRenderer() {
      override def calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, labelListener: DescriptorLabelListener): String = {
        descriptor.getValue match {
          case null => "null"
          case objRef: ObjectReference =>
            val typeName = if (objRef.referenceType() != null) ScalaCollectionRenderer.transformName(objRef.referenceType().name) else ""
            val sizeValue =
              if (!hasDefiniteSize(objRef, evaluationContext)) "?"
              else size(objRef, evaluationContext)
            typeName + sizePrefix + sizeValue
        }
      }
    }
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA))
    labelRenderer
  }

  private def evaluateBoolean(value: Value, context: EvaluationContext, evaluator: Evaluator): Boolean = {
    evaluate(value, context, evaluator) match {
      case b: BooleanValue => b.booleanValue()
      case x => throw EvaluationException(s"$x is not a boolean")
    }
  }

  private def evaluateInt(value: Value, context: EvaluationContext, evaluator: Evaluator): Int = {
    evaluate(value, context, evaluator) match {
      case i: IntegerValue => i.intValue()
      case x => throw EvaluationException(s"$x is not an integer")
    }
  }

  private def evaluate(value: Value, context: EvaluationContext, evaluator: Evaluator) = {
    if (value != null) {
      val newContext = context.createEvaluationContext(value)
      evaluator.exprEval.evaluate(newContext)
    }
    else if (value != null) {
      val newContext = context.createEvaluationContext(value)
      evaluator.exprEval.evaluate(newContext) match {
        case b: BooleanValue => b.booleanValue()
        case _ => throw EvaluationException("Cannot evaluate expression")
      }
    }
    else throw EvaluationException("Cannot evaluate expression")
  }

  object ScalaToArrayRenderer extends ReferenceRenderer(collectionClassName) with ChildrenRenderer {

    private lazy val toArrayEvaluator = {
      val classTagObjectEval = {
        val classTagEval = stableObjectEval("scala.reflect.ClassTag$")
        ScalaMethodEvaluator(classTagEval, "Object", null, Nil)
      }

      val manifestObjectEval = {
        val predefEval = stableObjectEval("scala.Predef$")
        val manifestEval = new ScalaMethodEvaluator(predefEval, "Manifest", null, Nil)
        new ScalaMethodEvaluator(manifestEval, "Object", null, Nil)
      }
      val argEval = ScalaDuplexEvaluator(classTagObjectEval, manifestObjectEval)

      new ScalaMethodEvaluator(new ScalaThisEvaluator(), "toArray", null, Seq(argEval))
    }

    override def getUniqueId: String = "ScalaToArrayRenderer"

    override def isExpandable(value: Value, context: EvaluationContext, parentDescriptor: NodeDescriptor): Boolean = {
      val evaluationContext: EvaluationContext = context.createEvaluationContext(value)
      try {
        return nonEmpty(value, context) && hasDefiniteSize(value, context)
      }
      catch {
        case e: EvaluateException =>
      }

      try {
        val children: Value = evaluateChildren(evaluationContext, parentDescriptor)
        val defaultChildrenRenderer: ChildrenRenderer = DebugProcessImpl.getDefaultRenderer(value.`type`)
        defaultChildrenRenderer.isExpandable(children, evaluationContext, parentDescriptor)
      }
      catch {
        case e: EvaluateException =>
          true
      }
    }

    override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiElement =
      ScalaPsiElementFactory.createExpressionFromText("this.toArray()", PsiManager.getInstance(context.getProject))


    override def buildChildren(value: Value, builder: ChildrenBuilder, evaluationContext: EvaluationContext) {
      val nodeManager: NodeManager = builder.getNodeManager
      try {
        val parentDescriptor: ValueDescriptor = builder.getParentDescriptor
        val childrenValue: Value = evaluateChildren(evaluationContext.createEvaluationContext(value), parentDescriptor)
        val renderer: NodeRenderer = getChildrenRenderer(childrenValue, parentDescriptor)
        renderer.buildChildren(childrenValue, builder, evaluationContext)
      }
      catch {
        case e: EvaluateException =>
          val errorChildren: util.ArrayList[DebuggerTreeNode] = new util.ArrayList[DebuggerTreeNode]
          errorChildren.add(nodeManager.createMessageNode(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + e.getMessage))
          builder.setChildren(errorChildren)
      }
    }

    private def getChildrenRenderer(childrenValue: Value, parentDescriptor: ValueDescriptor): NodeRenderer = {
      var renderer: NodeRenderer = ExpressionChildrenRenderer.getLastChildrenRenderer(parentDescriptor)
      if (renderer == null || childrenValue == null || !renderer.isApplicable(childrenValue.`type`)) {
        renderer = DebugProcessImpl.getDefaultRenderer(if (childrenValue != null) childrenValue.`type` else null)
        ExpressionChildrenRenderer.setPreferableChildrenRenderer(parentDescriptor, renderer)
      }
      renderer
    }


    private def stableObjectEval(name: String) = new ScalaFieldEvaluator(new TypeEvaluator(JVMNameUtil.getJVMRawText(name)), "MODULE$", false)

    private def evaluateChildren(context: EvaluationContext, descriptor: NodeDescriptor): Value = {
      val evaluator: ExpressionEvaluator = toArrayEvaluator.exprEval
      val value: Value = evaluator.evaluate(context)
      DebuggerUtilsEx.keep(value, context)
      value
    }
  }
}


