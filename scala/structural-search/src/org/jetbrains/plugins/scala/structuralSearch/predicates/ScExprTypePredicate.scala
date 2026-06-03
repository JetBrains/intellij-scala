package org.jetbrains.plugins.scala.structuralSearch.predicates

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.{MalformedPatternException, SSRBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResultExt, Typeable}

import java.util.regex.{Pattern, PatternSyntaxException}

class ScExprTypePredicate(val ty: String, baseName: String, val withinHierarchy: Boolean, val inverted: Boolean, val caseSensitive: Boolean, val isRegex: Boolean) extends MatchPredicate {
  private val pattern: Option[Pattern] = if isRegex then {
    try {
      Some(Pattern.compile(ty, if caseSensitive then 0 else Pattern.CASE_INSENSITIVE))
    } catch {
      case _: PatternSyntaxException =>
        throw new MalformedPatternException(SSRBundle.message("error.incorrect.regexp.constraint", ty, baseName))
      case _ => None
    }
  } else None
  private val types = ty.split('|').map(_.replace("\\.", ".").replace("\\(", "(").replace("\\)", ")"))

  override def `match`(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean = {
    matchedNode match {
      case func: ScFunction => matchName(func.`type`.getOrAny.toString, true)
      case expr: Typeable =>
        val typ = expr.`type`().getOrAny.widen.removeAliasDefinitions()(using Context(matchedNode))
        typ.extractClass match {
          case Some(cl) => matchClassOrSuper(cl)
          case None => matchName(typ.toString)
        }
      case _: LeafPsiElement => matchedNode.getParent match {
        case td: ScTypeDefinition => matchClassOrSuper(td)
        case scr: Typeable if scr.getParent.is[MethodInvocation] => matchName(scr.`type`().getOrAny.toString)
        case func: ScFunction => matchName(func.`type`.getOrAny.toString, true)
        case expr: Typeable =>
          val typ = expr.`type`().getOrAny.widen
          typ.extractClass match {
            case Some(cl) => matchClassOrSuper(cl)
            case None => matchName(typ.toString)
          }
        case _ => false
      }
      case _ => false
    }
  }

  private def matchClassOrSuper(cl: PsiClass): Boolean = {
    if (withinHierarchy) {
      matchSupers(cl, matchClass)
    } else {
      matchClass(cl)
    }
  }

  private def matchClass(cl: PsiClass): Boolean = {
    (cl.getName match {
      case null => false
      case n => matchName(n)
    }) || (cl.qualifiedName match {
      case null => false
      case n => matchName(n)
    })
  }

  private def matchName(name: String, ignoreWhiteSpace: Boolean = false): Boolean = {
    if (isRegex) {
      pattern.exists(_.matcher(name).matches())
    } else {
      val sname = if ignoreWhiteSpace then name.replaceAll("\\s", "") else name
      types.exists(ty => {
        val tyname = if ignoreWhiteSpace then ty.replaceAll("\\s", "") else ty
        if caseSensitive then sname == tyname else sname.equalsIgnoreCase(tyname)
      }) ^ inverted
    }
  }

  private def matchSupers(cl: PsiClass, matcher: PsiClass => Boolean): Boolean = {
    def matchSupers(cl: PsiClass): Boolean = {
      if (matcher(cl)) return true
      cl.getSupers.exists(matchSupers)
    }
    matchSupers(cl)
  }
}
