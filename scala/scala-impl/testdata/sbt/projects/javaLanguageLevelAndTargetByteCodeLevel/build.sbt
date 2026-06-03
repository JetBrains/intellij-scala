name := "java-language-level-and-target-byte-code-level"
version := "0.1"
scalaVersion := "2.13.4"

// Module naming: `source_target_release`
// `x` means option is missing
val module_x_x_x   = project.settings(javacOptions := Seq())

val module_8_8_x   = project.settings(javacOptions := Seq("-source", "8", "-target", "8"))
val module_8_11_x  = project.settings(javacOptions := Seq("-source", "8", "-target", "11"))
val module_11_8_x  = project.settings(javacOptions := Seq("-source", "11", "-target", "8"))
val module_11_11_x = project.settings(javacOptions := Seq("-source", "11", "-target", "11"))

val module_8_x_x  = project.settings(javacOptions := Seq("-source", "8"))
val module_11_x_x = project.settings(javacOptions := Seq("-source", "11"))
val module_14_x_x = project.settings(javacOptions := Seq("-source", "14"))
val module_15_x_x = project.settings(javacOptions := Seq("-source", "15"))

val module_x_8_x  = project.settings(javacOptions := Seq("-target", "8"))
val module_x_11_x = project.settings(javacOptions := Seq("-target", "11"))

val module_x_x_8  = project.settings(javacOptions := Seq("--release", "8"))
val module_x_x_11 = project.settings(javacOptions := Seq("--release", "11"))

// for version < 14 --enable-preview shouldn't affect anything cause there is no preview features in them
val module_8_x_x_preview = project.settings(javacOptions := Seq("-source", "8", "--enable-preview"))
val module_11_x_x_preview = project.settings(javacOptions := Seq("-source", "11", "--enable-preview"))
val module_14_x_x_preview = project.settings(javacOptions := Seq("-source", "14", "--enable-preview"))
val module_20_x_x_preview = project.settings(javacOptions := Seq("-source", "20", "--enable-preview"))

val module_x_x_8_preview  = project.settings(javacOptions := Seq("--release", "8", "--enable-preview"))
val module_x_x_11_preview = project.settings(javacOptions := Seq("--release", "11", "--enable-preview"))
val module_x_x_14_preview = project.settings(javacOptions := Seq("--release", "14", "--enable-preview"))
val module_x_x_20_preview = project.settings(javacOptions := Seq("--release", "20", "--enable-preview"))
