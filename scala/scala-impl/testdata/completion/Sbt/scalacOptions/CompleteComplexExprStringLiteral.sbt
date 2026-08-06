scalacOptions ++= {
  if (1 == 2) {
    Nil
  } else
    Seq("<caret>")
}

/*
-bootclasspath
-classpath
-Ydump-classes
*/