class ScalaGenericExtends[T <: ScalaGenericExtends[T]]

new ScalaGenericExtends[<caret>]
//TEXT: T <: ScalaGenericExtends[T], STRIKEOUT: false