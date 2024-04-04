package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, ClassRenderer, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, DescriptorWithParentObject, ValueDescriptor}
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

  def isApplicableFor(tpe: Type): Boolean = tpe match {
    case ct: ClassType => isScalaSource(ct)
    case _ => false
  }

  override def isEnabled: Boolean = true

  override def shouldDisplay(context: EvaluationContext, ref: ObjectReference, f: Field): Boolean =
    !ScalaSyntheticProvider.hasSpecialization(f, Some(ref.referenceType())) &&
      !isModule(f) && !isBitmap(f) && !isOffset(f)

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String = {
    val renderer = NodeRendererSettings.getInstance().getToStringRenderer
    renderer.calcLabel(descriptor, context, labelListener)
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = {
    val ref = value.asInstanceOf[ObjectReference]
    val project = context.getProject

    def buildFields(fields: Seq[Field]): CompletableFuture[Unit] = onDebuggerManagerThread(context) {
      val toShow = fields.filter(shouldDisplay(context, ref, _))
      val nodeManager = builder.getNodeManager
      if (toShow.isEmpty) {
        setClassHasNoFieldsToDisplayMessage(builder, nodeManager)
      } else {
        val nodes = toShow.map { field =>
          val desc =
            if (isLazyVal(ref, field)) LazyValDescriptor.create(project, ref, field)
            else new FieldDescriptorImpl(project, ref, field)
          nodeManager.createNode(desc, context)
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
  private val Module: String = ScalaBytecodeConstants.ObjectSingletonInstanceName

  private val Bitmap: String = "bitmap$"

  val Offset: String = "OFFSET$"

  def isModule(f: Field): Boolean = f.name() == Module

  def isBitmap(f: Field): Boolean = f.name().contains(Bitmap)

  def isOffset(f: Field): Boolean = f.name().startsWith(Offset)

  def isScalaSource(ct: ClassType): Boolean =
    Try(ct.sourceName().endsWith(".scala")).getOrElse(false)

  def isLazyVal(ref: ObjectReference, f: Field): Boolean = {
    val allFields = ref.referenceType().allFields().asScala
    val isScala3 = allFields.exists(_.name().startsWith(Offset))
    val fieldName = f.name()
    if (isScala3) {
      fieldName.contains("$lzy") && allFields.exists(isBitmap)
    } else {
      val allMethods = ref.referenceType().allMethods().asScala
      allMethods.exists { m =>
        val methodName = m.name()
        methodName.contains(s"$fieldName$$lzycompute")
      }
    }
  }
}
