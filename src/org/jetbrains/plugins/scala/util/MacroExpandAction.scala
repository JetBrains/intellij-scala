package org.jetbrains.plugins.scala.util

import java.io._
import java.util.regex.Pattern

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugin.scala.util.MacroExpansion
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScBlock, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.util.Key

import scala.annotation.tailrec


class MacroExpandAction extends AnAction {

  case class ResolvedMacroExpansion(expansion: MacroExpansion, psiElement: Option[SmartPsiElementPointer[PsiElement]])
  class UnresolvedExpansion extends Exception

  private val LOG = Logger.getInstance(getClass)

  override def actionPerformed(e: AnActionEvent): Unit = {
    implicit val currentEvent = e

    val sourceEditor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    val psiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(sourceEditor.getDocument)
    val candidates = psiFile match {
      case file: ScalaFile => findCandidatesInFile(file)
      case _ => Seq.empty
    }

    val expansions = deserializeExpansions(e)
    val filtered = expansions.filter { exp =>
      psiFile.getVirtualFile.getPath == exp.place.sourceFile
    }
    val ensugared = filtered.map(e => MacroExpansion(e.place, ensugarExpansion(e.body)))
    val resolved = tryResolveExpansionPlaces(ensugared)

    // if macro is under cursor, expand it, otherwise expand all macros in current file
    resolved
      .find(_.expansion.place.line == sourceEditor.getCaretModel.getLogicalPosition.line+1)
      .map(expandMacroUnderCursor)
      .getOrElse(expandAllMacroInCurrentFile(resolved))
  }


  def expandMacroUnderCursor(expansion: ResolvedMacroExpansion)(implicit e: AnActionEvent) = {
    inWriteCommandAction(e.getProject) {
      applyExpansion(expansion)
      e.getProject
    }
  }

  def expandAllMacroInCurrentFile(expansions: Seq[ResolvedMacroExpansion])(implicit e: AnActionEvent)  = {
    inWriteCommandAction(e.getProject) {
      applyExpansions(expansions.toList)
      e.getProject
    }
  }

  def findCandidatesInFile(file: ScalaFile): Seq[ScalaPsiElement] = {
    val buffer = scala.collection.mutable.ListBuffer[ScalaPsiElement]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitAnnotation(annotation: ScAnnotation) = {
        // TODO
      }
      override def visitMethodCallExpression(call: ScMethodCall) = {
        // TODO
      }
    }
    file.accept(visitor)
    buffer.toSeq
  }

  def applyExpansion(resolved: ResolvedMacroExpansion)(implicit e: AnActionEvent): Unit = {
    if (resolved.psiElement.isEmpty)
      throw new UnresolvedExpansion
    if (resolved.expansion.body.isEmpty) {
      LOG.warn(s"got empty expansion at ${resolved.expansion.place}, skipping")
      return
    }
    resolved.psiElement.get.getElement match {
      case (annot: ScAnnotation) =>
        expandAnnotation(annot, resolved.expansion)
      case (mc: ScMethodCall) =>
        expandMacroCall(mc, resolved.expansion)
      case (other) => () // unreachable
    }
  }

  def applyExpansions(expansions: Seq[ResolvedMacroExpansion], triedResolving: Boolean = false)(implicit e: AnActionEvent): Unit = {
    expansions match {
      case x::xs =>
        try {
          applyExpansion(x)
          applyExpansions(xs)
        }
        catch {
          case exc: UnresolvedExpansion if !triedResolving =>
            applyExpansions(tryResolveExpansionPlace(x.expansion) :: xs, triedResolving = true)
          case exc: UnresolvedExpansion if triedResolving =>
            LOG.warn(s"unable to expand ${x.expansion.place}, cannot resolve place, skipping")
            applyExpansions(xs)
        }
      case Nil =>
    }
  }

  def expandAnnotation(place: ScAnnotation, expansion: MacroExpansion)(implicit e: AnActionEvent) = {
    // we can only macro-annotate scala code
    place.getParent.getParent match {
      case holder: ScAnnotationsHolder =>
        val body = expansion.body
        val newPsi = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(body, PsiManager.getInstance(e.getProject))
        newPsi.firstChild match {
          case Some(block: ScBlock) => // insert content of block expression(annotation can generate >1 expression)
            val children = block.getChildren
            block.children.find(_.isInstanceOf[ScalaPsiElement]).foreach(p => p.putCopyableUserData(MacroExpandAction.EXPANDED_KEY, holder.getText))
            holder.getParent.addRangeAfter(children.tail.head, children.dropRight(1).last, holder)
            holder.delete()
          case Some(psi: PsiElement) => // defns/method bodies/etc...
            val result = holder.replace(psi)
            result.putCopyableUserData(MacroExpandAction.EXPANDED_KEY, holder.getText)
          case None => LOG.warn(s"Failed to parse expansion: $body")
        }
      case other =>  LOG.warn(s"Unexpected annotated element: $other at ${other.getText}")
    }
  }

  def expandMacroCall(call: ScMethodCall, expansion: MacroExpansion)(implicit e: AnActionEvent) = {
    val blockImpl = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(expansion.body, PsiManager.getInstance(e.getProject))
    val element = call.getParent.addAfter(blockImpl, call).asInstanceOf[ScBlock]
    element match {
      case ScBlock(x, _*) => x.putCopyableUserData(MacroExpandAction.EXPANDED_KEY, call.getText)
      case _ => // unreachable
    }
    call.delete()
  }

  def tryResolveExpansionPlace(expansion: MacroExpansion)(implicit e: AnActionEvent): ResolvedMacroExpansion = {
    ResolvedMacroExpansion(expansion, getRealOwner(expansion).map(new IdentitySmartPointer[PsiElement](_)))
  }

  def tryResolveExpansionPlaces(expansions: Seq[MacroExpansion])(implicit e: AnActionEvent): Seq[ResolvedMacroExpansion] = {
    expansions.map(tryResolveExpansionPlace)
  }

  def getRealOwner(expansion: MacroExpansion)(implicit e: AnActionEvent): Option[PsiElement] = {
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
      // handle macro calls with incorrect offset pointing to macro annotation
      // most likely it means given call is located inside another macro expansion
      case e: LeafPsiElement if expansion.place.macroApplication.matches("^[^\\)]+\\)$") =>
        val pos = e.getContainingFile.getText.indexOf(expansion.place.macroApplication)
        if (pos != -1)
          Some(e.getContainingFile.findElementAt(pos))
        else
          None
      // macro annotations
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

  def isMacroAnnotation(expansion: MacroExpansion)(implicit e: AnActionEvent): Boolean = {
    getRealOwner(expansion) match {
      case Some(_: ScAnnotation) => true
      case Some(_: ScMethodCall) => false
      case Some(other)           => false
      case None                  => false
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
      "def this\\(\\) = \\{\\s*this\\(\\);\\s*\\(\\)\\s*\\};" -> "", // remove invalid super constructor calls
      "_root_."             -> ""         // _root_ package is obsolete
    )

    applyRules(rules)
  }

  def deserializeExpansions(implicit event: AnActionEvent): Seq[MacroExpansion] = {
    val file = new File(PathManager.getSystemPath + s"/expansion-${event.getProject.getName}")
    if (!file.exists()) return Seq.empty
    val fs = new BufferedInputStream(new FileInputStream(file))
    val os = new ObjectInputStream(fs)
    val res = scala.collection.mutable.ListBuffer[MacroExpansion]()
    while (fs.available() > 0) {
      res += os.readObject().asInstanceOf[MacroExpansion]
    }
    res
  }
}

object MacroExpandAction {

  val EXPANDED_KEY = new Key[String]("MACRO_EXPANDED_KEY")
}
