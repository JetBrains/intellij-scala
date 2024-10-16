package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.messages

object CompilerType {
  private val TypeKey: Key[String] = Key.create("SCALA_COMPILER_TYPE_KEY")

  private val TypeRequestedKey: Key[AnyRef] = Key.create("SCALA_COMPILER_TYPE_REQUESTED_KEY")

  val Topic: messages.Topic[Listener] = messages.Topic.create("completion events", classOf[Listener])

  def apply(e: PsiElement): Option[String] =
    Option(e.getCopyableUserData(TypeKey))

  def update(e: PsiElement, tpe: Option[String]): Unit = {
    e.putCopyableUserData(TypeKey, tpe.orNull)
  }

  def requestFor(e: PsiElement): Unit = {
    // Request only once per element
    if (e.getUserData(TypeRequestedKey) == null) {
      e.putUserData(TypeRequestedKey, "")
      e.getProject.getMessageBus.syncPublisher(Topic).onCompilerTypeRequest(e)
    }
  }

  trait Listener {
    def onCompilerTypeRequest(e: PsiElement): Unit
  }
}
