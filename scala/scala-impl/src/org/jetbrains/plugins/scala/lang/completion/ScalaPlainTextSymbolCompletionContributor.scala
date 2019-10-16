package org.jetbrains.plugins.scala.lang.completion

import java.util
import java.util.Collections

import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class ScalaPlainTextSymbolCompletionContributor extends PlainTextSymbolCompletionContributor {
  override def getLookupElements(file: PsiFile
                                 , invocationCount: Int
                                 , prefix: String): util.Collection[LookupElement] = {
    file match {
      case scalaFile: ScalaFile =>
        val result = new util.ArrayList[LookupElement]
        for (typeDefinition <- scalaFile.typeDefinitions) {
          val name = typeDefinition.name
          if (name != null) {
            result.add(LookupElementBuilder.create(name).withIcon(typeDefinition.getIcon(0)))
            val infix = getInfix(prefix, name)
            if (infix != null) {
              val offset = name.length + infix.length
              val memberPrefix = prefix.substring(0, offset)
              val rest = prefix.substring(offset)
              processClassBody(invocationCount, result, typeDefinition, infix, memberPrefix, rest)
            }
            else if (invocationCount > 0) {
              processClassBody(invocationCount, result, typeDefinition, infix, null, null)
            }
          }
        }
        result
      case _ => Collections.emptyList()
    }
  }

  private def processClassBody(invocationCount: Int
                               , result: util.List[LookupElement]
                               , aClass: ScTypeDefinition
                               , infix: String
                               , memberPrefix: String
                               , rest: String): Unit = {
    for (member <- aClass.members) {
      member match {
        case namedMember: ScNamedElement if namedMember.isPhysical =>
          val memberName = namedMember.getName
          if (memberName != null) {
            val icon = member.getIcon(0)
            val element = LookupElementBuilder.create(memberName).withIcon(icon)
            if (invocationCount > 0) result.add(element)
            if (memberPrefix != null) {
              if (member.isInstanceOf[ScFunction] ||
                member.isInstanceOf[ScValueOrVariable] && !(infix == "::") || infix == ".") {
                result.add(LookupElementBuilder.create(memberPrefix + memberName).withIcon(icon))
                member match {
                  case clazz: ScTypeDefinition =>
                    val nestedInfix = getInfix(rest, memberName)
                    if (nestedInfix != null) {
                      val index = memberName.length + nestedInfix.length
                      val nestedPrefix = memberPrefix + rest.substring(0, index)
                      processClassBody(0, result, clazz, nestedInfix, nestedPrefix, rest.substring(index))
                    }
                  case _ =>
                }
              }
            }
          }
        case _ =>
      }
    }
  }

  private def getInfix(currentPrefix: String, className: String): String = {
    if (!currentPrefix.startsWith(className)) return null
    List(".", "#", "::")
      .find(infix => currentPrefix.startsWith(infix, className.length))
      .orNull
  }

}
