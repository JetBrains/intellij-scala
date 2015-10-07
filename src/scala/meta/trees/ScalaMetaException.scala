package scala.meta.trees

import com.intellij.psi.PsiElement

import scala.meta.internal.{ast => m, semantic => h, AbortException}

class ScalaMetaException(message: String) extends Exception(message)

class ScalaMetaUnreachableException(reason: String = "") extends ScalaMetaException(s"This code should be unreachable${if (reason.nonEmpty) s": $reason"}")

class ScalaMetaUnexpectedPSI(val elem: PsiElement) extends ScalaMetaException(s"Got unexpected ${elem.getClass} at ${elem.getText}")

class ScalaMetaUnexpectedTree(val tree: m.Tree) extends ScalaMetaException(s"Got unexpected ${tree.getClass} at ${tree.toString()}")

class ScalaMetaResolveError(elem: PsiElement) extends ScalaMetaException(s"Cannot resolve ${elem.getClass} at ${elem.toString}")

class ScalaMetaTypeResultFailure(elem: Option[PsiElement], cause: String) extends ScalaMetaException(s"Cannot calculate type at ${elem.map(_.getText).getOrElse("UNKNOWN")}($cause)")

//package object error {
//  def unreachable = throw new AbortException()
//}
