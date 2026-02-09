package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.spellchecker.inspections.CommentSplitter
import com.intellij.spellchecker.tokenizer.{TokenConsumer, Tokenizer}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}

/**
 * @see [[com.intellij.spellchecker.DocCommentTokenizer]] for the Java version
 */
private class ScalaDocCommentTokenizer extends Tokenizer[PsiDocComment] {
  override def tokenize(comment: PsiDocComment, consumer: TokenConsumer): Unit = {
    val splitter = CommentSplitter.getInstance
    comment.children.foreach {
      case tag: PsiDocTag =>
        if (!ScalaDocCommentTokenizer.ExcludedTags.contains(tag.name))
          for (data <- tag.getDataElements)
            consumer.consumeToken(data, splitter)
      case el =>
        consumer.consumeToken(el, splitter)
    }
  }
}

private[textAnalysis] object ScalaDocCommentTokenizer {
  val ExcludedTags: Set[String] = Set(
    "author",
    "see",
  )
}
