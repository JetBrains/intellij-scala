sourceDirectory in Jmh := (sourceDirectory in Test).value
classDirectory in Jmh := (classDirectory in Test).value
dependencyClasspath in Jmh := (dependencyClasspath in Test).value
// rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
compile in Jmh := (compile in Jmh).dependsOn(compile in Test).value
run in Jmh := (run in Jmh).dependsOn(compile in Jmh).evaluated

//benchmarks should be run from the console

//>sbt
//>project jmhBenchmarks
//>jmh:run <something>
//
//>jmh:run -h    //to get list of possible parameters