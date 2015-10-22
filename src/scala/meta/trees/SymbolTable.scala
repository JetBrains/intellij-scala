package scala.meta.trees

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.psi._
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.{api => p, impl, types => ptype}
import org.scalameta.collections._

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.meta.internal.{ast => m, semantic => h}

trait SymbolTable {
  self: TreeConverter =>

  private val symbolCache = TwoWayCache[PsiElement, h.Symbol]()

  def localSymbolDelim = "*"

  def isLocal(elem: PsiElement): Boolean = {
    elem match {
      case e: ScTypeDefinition => e.isLocal
      case e: ScBindingPattern if e.containingClass == null => true
      case pp: params.ScParameter => true
      case tp: ScTypeParam => true
      case _ => false
    }
  }

  def fqnameToSymbol(fqName: String, toDrop: Int = 1): h.Symbol = {
    fqName
      .split('.')
      .dropRight(toDrop)
      .foldLeft(h.Symbol.RootPackage.asInstanceOf[h.Symbol])((parent, name) => h.Symbol.Global(parent, name, h.Signature.Term))
  }

  def ownerSymbol(elem: PsiElement): h.Symbol = {
    elem match {
      case mm: ScMember =>
        if (mm.containingClass == null) h.Symbol.EmptyPackage else toSymbol(mm.containingClass)
      case bp: ScBindingPattern =>
        if (bp.containingClass == null) h.Symbol.EmptyPackage else toSymbol(bp.containingClass)
      case pc: PsiMember =>
        if (pc.getContainingClass == null) h.Symbol.EmptyPackage else toSymbol(pc.getContainingClass)
      case other => other ?!
    }
  }

  def toSymbol(elem: PsiElement): h.Symbol = {
    def convert = elem match {
      case _ if isLocal(elem) =>
        toLocalSymbol(elem)
      case sc: impl.toplevel.synthetic.ScSyntheticClass =>
        h.Symbol.Global(fqnameToSymbol(sc.getQualifiedName), sc.className, h.Signature.Type)
      case td: ScTypeDefinition if !td.qualifiedName.contains(".") => // empty package defn
        h.Symbol.Global(h.Symbol.EmptyPackage, td.name, h.Signature.Type)
      case td: ScTemplateDefinition =>
        h.Symbol.Global(fqnameToSymbol(td.qualifiedName), td.name, h.Signature.Type)
      case td: ScFieldId =>
        val owner = td.nameContext match {
          case vd: ScValueDeclaration => ownerSymbol(vd)
          case vd: ScVariableDeclaration => ownerSymbol(vd)
          case other => other ?!
        }
        h.Symbol.Global(owner, td.name, h.Signature.Term)
      case td: ScFunction =>
        // TODO: meta trees don't resolve unapply methods(or do they?)
        if (td.name == "unapply")
          toSymbol(td.containingClass)
        else {
          val jvmsig = DebuggerUtil.getFunctionJVMSignature(td).getName(null)
          h.Symbol.Global(ownerSymbol(td), td.name, h.Signature.Method(jvmsig))
        }
      case pc: ScPackaging =>
        toSymbol(pc.reference.get.bind().get.element)
      case cr: ScStableCodeReferenceElement =>
        toSymbol(cr.resolve())
      case ta: ScTypeAlias =>
        h.Symbol.Global(ownerSymbol(ta), ta.name, h.Signature.Type)
      case bp: ScBindingPattern =>
        h.Symbol.Global(ownerSymbol(bp), bp.name, h.Signature.Term)
      // Java stuff starts here
      case pc: PsiClass =>
        h.Symbol.Global(ownerSymbol(pc), pc.getName, h.Signature.Type)
      case pm: PsiMethod =>
        h.Symbol.Global(ownerSymbol(pm), pm.getName, h.Signature.Method(JVMNameUtil.getJVMSignature(pm).getName(null)))
      case pp: PsiPackage if pp.getName == null =>
        h.Symbol.RootPackage
      case pc: PsiPackage =>
        h.Symbol.Global(toSymbol(pc.getParentPackage), pc.getName, h.Signature.Term)
      case _ => elem ?!
    }
    symbolCache.getOrElseUpdate(elem, convert)
  }

  def toLocalSymbol(elem: PsiElement): h.Symbol = {
      @tailrec
      def getFile(elem: PsiElement): PsiFile =  elem.getContainingFile match {
        case _: DummyHolder => getFile(elem.getParent)
        case _ => elem.getContainingFile
      }
      val url = try {
        getFile(elem).getVirtualFile.getUrl
      } catch {
        case _: NullPointerException => "UNRESOLVED"
      }
      h.Symbol.Local(url + localSymbolDelim + elem.getTextOffset)
  }

  def fromSymbol(sym: h.Symbol): PsiElement = {
    def getFqName(sym: h.Symbol): (String, Option[String]) = sym match {
      case h.Symbol.Global(owner, name, signature) =>
        signature match {
          case h.Signature.Type | h.Signature.Term =>
            (s"${getFqName(owner)._1}.$name", None)
          case h.Signature.Method(jvmsig) =>
            (s"${getFqName(owner)._1}.$name", Some(jvmsig))
          case h.Signature.TypeParameter =>
            ???
          case h.Signature.TermParameter =>
            ???
        }
      case other => unreachable("can't get fqn of non-global symbol")
    }

    def convert: PsiElement = sym match {
      case h.Symbol.Local(id) =>
        val url::pos = id.split(localSymbolDelim).toList
        findFileByPath(url).findElementAt(pos.head.toInt)
      case h.Symbol.RootPackage => new PsiPackageImpl(PsiManager.getInstance(getCurrentProject), "")
      case h.Symbol.EmptyPackage => new PsiPackageImpl(PsiManager.getInstance(getCurrentProject), "")
      case h.Symbol.Zero => unreachable("can't map Zero symbol")
      case h.Symbol.Global(owner, name, signature) =>
        getFqName(sym) match {
          case (fqn, Some(jvmSig)) =>
            val clazz = ScalaPsiManager.instance(getCurrentProject).getCachedClass(
              GlobalSearchScope.projectScope(getCurrentProject), fqn.split('.').drop(1).mkString(".")
            )
            clazz match {
              case sc: ScTemplateDefinition =>
//                sc.findMethodBySignature()
                ???
              case pc: PsiClass =>
                ???
            }
          case (fqn, None) =>
            ScalaPsiManager.instance(getCurrentProject).getCachedClass(
              GlobalSearchScope.projectScope(getCurrentProject), fqn.split('.').drop(1).mkString(".")
            ).get
        }
    }

    symbolCache.getOrElseUpdate(sym, convert)
  }
}
