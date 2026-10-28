package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, ClassRenderer, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, DescriptorWithParentObject, NodeDescriptor, ValueDescriptor}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.ui.util._
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScalaClassRenderer extends ClassRenderer {

  import ScalaClassRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  override def getName: String = DebuggerBundle.message("scala.class.renderer")

  override def clone(): ClassRenderer = super.clone()

  def isApplicableFor(tpe: Type): Boolean = tpe match {
    case ct: ClassType => isScalaSource(ct) && !isStringBuilder(ct)
    case _ => false
  }

  override def isEnabled: Boolean = true

  override def shouldDisplay(context: EvaluationContext, ref: ObjectReference, f: Field): Boolean =
    !ScalaSyntheticProvider.hasSpecialization(f, Some(ref.referenceType())) &&
      !isScalaSpecialField(f)

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String = {
    val renderer = NodeRendererSettings.getInstance().getToStringRenderer
    renderer.calcLabel(descriptor, context, labelListener)
  }

  final override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit =
    buildChildrenOrShowError(builder)(buildChildrenAsync(value, builder, context))

  /**
   * Adds the children of `value` to `builder`. If they cannot be built, the returned future fails and [[buildChildren]]
   * shows the error in the node, instead of leaving it at "Collecting data".
   */
  protected def buildChildrenAsync(value: Value, builder: ChildrenBuilder, context: EvaluationContext): CompletableFuture[Unit] = {
    val ref = value.asInstanceOf[ObjectReference]
    val project = context.getProject

    def buildFields(fields: Seq[Field]): CompletableFuture[Unit] = onDebuggerManagerThread(context) {
      val toShow = fields.filter(shouldDisplay(context, ref, _))
      val nodeManager = builder.getNodeManager
      if (toShow.isEmpty) {
        setClassHasNoFieldsToDisplayMessage(builder, nodeManager)
      } else {
        val nodes = toShow.map { field =>
          nodeManager.createNode(fieldDescriptor(project, ref, field, context), context)
        }
        builder.setChildren(nodes.asJava)
      }
    }

    for {
      fields <- DebuggerUtilsAsync.allFields(ref.referenceType())
      _ <- buildFields(fields.asScala.toSeq)
    } yield ()
  }

  override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiElement =
    node.getDescriptor match {
      case _: DescriptorWithParentObject => super.getChildValueExpression(node, context)
      case _ => null
    }
}

private object ScalaClassRenderer {
  private val LOG = Logger.getInstance(classOf[ScalaClassRenderer])

  private val Module: String = ScalaBytecodeConstants.ObjectSingletonInstanceName

  private val Bitmap: String = "bitmap$"

  private final val OffsetPrefix = "OFFSET$"

  private final val VarHandleSuffix = "$lzyHandle"

  private def isScalaSpecialField(f: Field): Boolean = {
    val name = f.name()
    name == Module || name.contains(Bitmap) || name.endsWith(VarHandleSuffix) || name.startsWith(OffsetPrefix)
  }

  private def fieldDescriptor(project: Project, ref: ObjectReference, field: Field, context: EvaluationContext): NodeDescriptor =
    if (!isLazyVal(ref, field)) new FieldDescriptorImpl(project, ref, field)
    else
      try LazyValDescriptor.create(project, ref, field, context.asInstanceOf[EvaluationContextImpl])
      catch {
        // A lazy val that is compiled in a way the renderer does not know must not hide the other fields
        case e: EvaluateException =>
          LOG.error(s"Cannot show the field ${field.name()} of ${ref.referenceType().name()} as a lazy val", e)
          new FieldDescriptorImpl(project, ref, field)
      }

  def isScalaSource(ct: ClassType): Boolean =
    Try(ct.sourceName().endsWith(".scala")).getOrElse(false)

  def isLazyVal(ref: ObjectReference, f: Field): Boolean = {
    val allFields = ref.referenceType().allFields().asScala
    val isScala3 = allFields.exists(f => isVarHandleOrOffsetField(f.name()))
    val fieldName = f.name()
    if (isScala3) fieldName.contains("$lzy")
    else {
      val allMethods = ref.referenceType().allMethods().asScala
      allMethods.exists { m =>
        val methodName = m.name()
        methodName.contains(s"$fieldName$$lzycompute")
      }
    }
  }

  private[ui] def isVarHandleOrOffsetField(fieldName: String): Boolean =
    fieldName.endsWith(VarHandleSuffix) || fieldName.startsWith(OffsetPrefix)

  private[ui] def isStringBuilder(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.mutable.StringBuilder")
}
