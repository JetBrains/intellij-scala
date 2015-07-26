package org.jetbrains.plugins.scala.util

import java.io.{EOFException, FileInputStream, ObjectInputStream}
import java.util.regex.Pattern

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugin.scala.util.MacroExpansion
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScMethodCall}

import scala.annotation.tailrec


// Stub for lazy people, who want to test some stuff on a key press
// but too lazy to implement new action/modify existing one.
class DummyAction extends AnAction {

  override def actionPerformed(e: AnActionEvent): Unit = {
    implicit val currentEvent = e

    val expansions = deserializeExpansions(e)
    for (expansion <- expansions) {
      val exp = ensugarExpansion(expansion.body)
      val isma = isMacroAnnotation(expansion)
      ""
    }
  }

  def isMacroAnnotation(expansion: MacroExpansion)(implicit e: AnActionEvent): Boolean = {
    def getRealOwner(expansion: MacroExpansion): Option[PsiElement] = {
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + expansion.place.sourceFile)
      val psiFile = PsiManager.getInstance(e.getProject).findFile(virtualFile)
      psiFile.findElementAt(expansion.place.offset) match {
        // macro method call has offset pointing to '(', not method name
        case e: LeafPsiElement if e.findReferenceAt(0) == null =>
          def walkUp(elem: PsiElement = e): Option[PsiElement] = elem match {
            case null => None
            case m: ScMethodCall => Some(m)
            case e: PsiElement => walkUp(e.getParent)
          }
          walkUp()
        case e: LeafPsiElement =>
          def walkUp(elem: PsiElement = e): Option[PsiElement] = elem match {
            case null => None
            case a: ScAnnotation => Some(a)
            case e: PsiElement => walkUp(e.getParent)
          }
          walkUp()
        case _ => None
      }
    }
    getRealOwner(expansion) match {
      case Some(_: ScAnnotation) => true
      case Some(_: ScMethodCall) => false
      case None => false
    }
  }

  def ensugarExpansion(text: String): String = {

    @tailrec
    def applyRules(rules: Seq[(String, String)], input: String = text): String = {
      def pat(p: String) = Pattern.compile(p, Pattern.DOTALL | Pattern.MULTILINE)
      rules match {
        case (pattern, replacement)::xs => applyRules(xs, pat(pattern).matcher(input).replaceAll(replacement))
        case Nil                        => input
      }
    }

    val rules = Seq(
      "\\<init\\>"          -> "this",    // replace constructor names
      " *\\<[a-z]+\\> *"    -> "",        // remove compiler attributes
      "super\\.this\\(\\);" -> "this();", // replace super constructor calls
      "def this\\(\\) = \\{\\s*this\\(\\);\\s*\\(\\)\\s*\\};" -> "" // remove invalid super constructor calls
    )

    applyRules(rules)
  }

  def deserializeExpansions(implicit event: AnActionEvent): Seq[MacroExpansion] = {
    val fs = new FileInputStream(PathManager.getSystemPath + s"/expansion-${event.getProject.getName}")
    val os = new ObjectInputStream(fs)
    val res = scala.collection.mutable.ListBuffer[MacroExpansion]()
    while (true) {
      try {
        res += os.readObject().asInstanceOf[MacroExpansion]
      } catch {
        case _:EOFException => return res
      }
    }
    res
  }
}
