package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.psi._
import util.proximity.ProximityWeigher
import util.ProximityLocation
import java.lang.Integer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScValue}

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaExplicitlyImportedWeigher extends ProximityWeigher {
  def applyQualifier(qual: String, position: PsiElement): Option[Integer] = {
    val index = qual.lastIndexOf('.')
    val qualNoPoint = if (index < 0) null else qual.substring(0, index)
    val context = ScalaPsiUtil.getContextOfType(position, false, classOf[ScalaFile])
    if (context != null) {
      val scalaFile: ScalaFile = context.asInstanceOf[ScalaFile]
      var accepted = false
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitImportExpr(expr: ScImportExpr) {
          if (accepted) return
          if (expr.singleWildcard && qualNoPoint != null) {
            for (resolve <- expr.qualifier.multiResolve(false))
              resolve match {
                case ScalaResolveResult(pack: PsiPackage, _) =>
                  if (qualNoPoint == pack.getQualifiedName) accepted = true
                case ScalaResolveResult(clazz: PsiClass, _) =>
                  if (qualNoPoint == clazz.getQualifiedName) accepted = true
                case _ =>
              }
          } else if (expr.selectorSet != None) {
            for (selector <- expr.selectors) {
              for (resolve <- selector.reference.multiResolve(false)) {
                resolve match {
                  case ScalaResolveResult(clazz: PsiClass, _) =>
                    if (qual == clazz.getQualifiedName) accepted = true
                  case _ =>
                }
              }
            }
          } else {
            expr.reference match {
              case Some(ref) =>
                for (resolve <- ref.multiResolve(false))
                  resolve match {
                    case ScalaResolveResult(clazz: PsiClass, _) =>
                      if (qual == clazz.getQualifiedName) accepted = true
                    case _ =>
                  }
              case None =>
            }
          }
        }
      }
      scalaFile.accept(visitor)
      if (accepted) return Some(2)
      if (qualNoPoint != null && qualNoPoint == "scala" ||
        qualNoPoint == "java.lang" || qualNoPoint == "scala.Predef") {
        return Some(1)
      }
    }
    None
  }

  def applyToMember(member: ScMember, position: PsiElement): Option[Integer] = {
    member.getContext match {
      case tb: ScTemplateBody =>
        val clazz: PsiClass = member.getContainingClass
        clazz match {
          case obj: ScObject =>
            val qualNoPoint = obj.getQualifiedName
            if (qualNoPoint != null) {
              val qual = qualNoPoint + "." + member.getName
              applyQualifier(qual, position) match {
                case Some(x) => return Some(x)
                case None =>
              }
            }
          case _ =>
        }
      case _ =>
    }
    None
  }

  def weigh(element: PsiElement, location: ProximityLocation): Integer = {
    val position: PsiElement = location.getPosition
    if (position == null) {
      return 0
    }
    val elementFile: PsiFile = element.getContainingFile
    val positionFile: PsiFile = position.getContainingFile
    if (!positionFile.isInstanceOf[ScalaFile]) return 0
    if (positionFile != null && elementFile != null && positionFile.getOriginalFile == elementFile.getOriginalFile) {
      return 3
    }
    element match {
      case clazz: PsiClass if clazz.getQualifiedName != null =>
        val qual: String = clazz.getQualifiedName
        applyQualifier(qual, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: ScMember =>
        applyToMember(member, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: PsiMember if member.hasModifierProperty("static") =>
        val clazz = member.getContainingClass
        val qualNoPoint = clazz.getQualifiedName
        if (qualNoPoint != null) {
          val qual = qualNoPoint + "." + member.getName
          applyQualifier(qual, position) match {
            case Some(x) => return x
            case None =>
          }
        }
      case b: ScBindingPattern =>
        ScalaPsiUtil.nameContext(b) match {
          case v: ScValue =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case v: ScVariable =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case _ =>
        }
      case _ =>
    }
    return 0
  }
}