package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.EditorStates.reservedDebugNames

import scala.collection.mutable

package object scala3 {

  case class EditorState private(text: String, textToType: String) {
    def withTransformedText(f: String => String): EditorState = EditorState(f(text), textToType)
  }
  object EditorState {
    def apply(text: String, textToType: String): EditorState =
      new EditorState(text.withNormalizedSeparator, textToType.withNormalizedSeparator)

    def apply(text: String, textToType: TypeText): EditorState =
      new EditorState(text, textToType.text)
  }

  case class EditorStates private(debugName: Option[String], states: Seq[EditorState]) {
    debugName.foreach { name =>
      reservedDebugNames.synchronized {
        if (reservedDebugNames.contains(name))
          throw new AssertionError(s"editor states debug name is not unique: '$name'")
        else
          reservedDebugNames += name
      }
    }
  }
  object EditorStates {
    private val reservedDebugNames = mutable.HashSet.empty[String]

    def apply(debugName: String, states: Seq[(String, TypeText)])
             (implicit d: DummyImplicit): EditorStates =
      new EditorStates(Some(debugName), states.map(t => EditorState(t._1, t._2)))

    def apply(debugName: String, states: Seq[EditorState])
             (implicit d: DummyImplicit, d2: DummyImplicit): EditorStates =
      new EditorStates(Some(debugName), states)

    def apply(states: (String, TypeText)*)
             (implicit d: DummyImplicit): EditorStates =
      new EditorStates(None, states.map(t => EditorState(t._1, t._2)))

    def apply(debugName: String, before: String, textToType: TypeText, after: String): EditorStates = {
      val states: Seq[EditorState] = Seq(EditorState(before, textToType), EditorState(after, ""))
      new EditorStates(Some(debugName), states)
    }
  }

  case class TypeText(text: String)
  object TypeText {
    val Enter: TypeText = TypeText("\n")
    val Ignored: TypeText = TypeText("")
  }
}
