name := "javac-options-per-module"
version := "0.1"
scalaVersion := "2.13.4"

javacOptions                       ++= Seq("root_option")
javacOptions in Compile            ++= Seq("root_option_in_compile")
javacOptions in (Compile, compile) ++= Seq("root_option_in_compile_compile")

val module1 = project.settings(javacOptions                       ++= Seq("module_1_option"))
val module2 = project.settings(javacOptions in Compile            ++= Seq("module_2_option_in_compile"))
val module3 = project.settings(javacOptions in (Compile, compile) ++= Seq("module_3_option_in_compile_compile"))