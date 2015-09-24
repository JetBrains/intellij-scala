package scala.meta.trees

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScFunctionDefinition, ScMacroDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.rearranger.ScalaArrangementVisitor

import scala.meta._
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.meta.internal.{ast=>m}

trait MemberAdapter {
  self: TreeConverter =>

  def getDefns(name: m.Name): Seq[Member] = {
    val psi = name.denot.symbols.map(fromSymbol)
    val members = scala.collection.mutable.ListBuffer[Member]()
    val visitor = new ScalaElementVisitor {
      override def visitElement(element: ScalaPsiElement) = element ?!
      override def visitElement(element: PsiElement) = element ?!
      override def visitTypeDefinition(typedef: ScTypeDefinition) = {
        val res = typedef match {
          case e: ScTrait => toTrait(e)
          case e: ScClass => toClass(e)
          case e: ScObject => toObject(e)
        }
        members += res
      }
      override def visitFunctionDefinition(fun: ScFunctionDefinition) = {
        members += toFunDefn(fun)
      }
      override def visitMacroDefinition(fun: ScMacroDefinition) = {
        members += toMacroDefn(fun)
      }
    }
    psi.foreach(_.accept(visitor))
    members.toStream
  }

  def getMembers(name: m.Name): Seq[Member] = {
    def getMembers(clazz: PsiElement): Seq[Member] = {
      clazz match {
        case c: ScTemplateDefinition =>
          c.members.toStream.map(ideaToMeta(_).asInstanceOf[Member])
      }
    }
    val psi = name.denot.symbols.map(fromSymbol)
    psi.flatMap(getMembers)
  }

}
