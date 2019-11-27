//package org.jetbrains.plugins.scala.ant;
//
//import com.intellij.compiler.ant.*;
//import com.intellij.compiler.ant.taskdefs.PatternSetRef;
//import com.intellij.openapi.module.Module;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.util.Pair;
//import org.jetbrains.annotations.NonNls;
//import org.jetbrains.annotations.Nullable;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * @author ilyas, Constatntine Plotnikov
// */
//public class ScalaAntCustomCompilerProvider extends ChunkCustomCompilerExtension {
//  /**
//   * The property for scalac task SDK
//   */
//  @NonNls
//  private final static String SCALAC_TASK_SDK_PROPERTY = "scalac.task.sdk";
//
//  /**
//   * {@inheritDoc}
//   */
//  public void generateCustomCompile(Project project,
//                                    ModuleChunk chunk,
//                                    GenerationOptions genOptions,
//                                    boolean compileTests,
//                                    CompositeGenerator generator,
//                                    Tag compilerArgs,
//                                    Tag bootclasspathTag,
//                                    Tag classpathTag,
//                                    PatternSetRef compilerExcludes,
//                                    Tag srcTag,
//                                    String outputPathRef) {
//    Tag scalac = new Tag("scalac", Pair.create("destdir", outputPathRef));
//    // note that boot classpath tag is ignored
//    scalac.add(srcTag);
//    scalac.add(classpathTag);
//    scalac.add(compilerExcludes);
//    generator.add(scalac);
//
//
//    final Pair<String, String> classpathRef = Pair.create("refid", BuildProperties.getClasspathProperty(chunk.getName()));
//    final Tag cp2 = new Tag("classpath");
//    cp2.add(new Tag("pathelement", Pair.create("location", outputPathRef)));
//    cp2.add(new Tag("path", classpathRef));
//    if (compileTests) {
//      cp2.add(new Tag("pathelement", Pair.create("location", BuildProperties.propertyRef(
//          BuildProperties.getOutputPathProperty(chunk.getName())))));
//    }
//
//    final Tag javac = new Tag("javac", getJavacAttributes(genOptions, outputPathRef, chunk.getName()));
//    javac.add(compilerArgs);
//    javac.add(bootclasspathTag);
//    javac.add(cp2);
//    javac.add(srcTag);
//    javac.add(compilerExcludes);
//
//    generator.add(javac);
//
//  }
//
//  protected static Pair<String, String> pair(@NonNls String v1, @NonNls String v2) {
//    if (v2 == null) {
//      return null;
//    }
//    return new Pair<String, String>(v1, v2);
//  }
//
//
//  private static Pair[] getJavacAttributes(GenerationOptions genOptions, String outputDir, String moduleName) {
//    final List<Pair> pairs = new ArrayList<Pair>();
//    pairs.add(pair("destdir", outputDir));
//    pairs.add(pair("debug", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_DEBUG_INFO)));
//    pairs.add(pair("nowarn", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_NO_WARNINGS)));
//    pairs.add(pair("memorymaximumsize", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_MAX_MEMORY)));
//    pairs.add(pair("fork", "true"));
//    if (genOptions.forceTargetJdk) {
//      pairs.add(pair("executable", getExecutable(moduleName)));
//    }
//    return pairs.toArray(new Pair[pairs.size()]);
//  }
//
//  @Nullable
//  @NonNls
//  private static String getExecutable(String moduleName) {
//    if (moduleName == null) {
//      return null;
//    }
//    return BuildProperties.propertyRef(BuildProperties.getModuleChunkJdkBinProperty(moduleName)) + "/javac";
//  }
//
//
//  /**
//   * {@inheritDoc}
//   */
//  public void generateCustomCompilerTaskRegistration(Project project, GenerationOptions genOptions, CompositeGenerator generator) {
////    // find SDK library with maximum version number in order to use for compiler
////    final Library[] libraries = ScalaCompilerUtil.getAllScalaCompilerLibraries(project);
////    if (libraries.length == 0) {
////      // no SDKs in the project, the task registration is not generated
////      return;
////    }
////    final Collection<String> versions = ScalaCompilerUtil.getScalaCompilerVersions(project);
////    String maxVersion = versions.isEmpty() ? null : Collections.max(versions);
////    Library sdkLib = null;
////    for (Library lib : libraries) {
////      if (maxVersion == null || maxVersion.equals(ScalaCompilerUtil.getScalaCompilerLibVersion(lib))) {
////        sdkLib = lib;
////      }
////    }
////    assert sdkLib != null;
////    String scalaSdkPathRef = BuildProperties.getLibraryPathId(sdkLib.getName());
////    generator.add(new Property(SCALAC_TASK_SDK_PROPERTY, scalaSdkPathRef));
////    Tag taskdef = new Tag("taskdef", Pair.create("name", "scalac"), Pair.create("classname", "scala.tools.ant.Scalac"),
////        Pair.create("classpathref", "${" + SCALAC_TASK_SDK_PROPERTY + "}"));
////    generator.add(taskdef);
//  }
//
//  /**
//   * {@inheritDoc}
//   */
//  public boolean hasCustomCompile(ModuleChunk chunk) {
//    for (Module m : chunk.getModules()) {
////      if (ScalaFacet.getInstance(m) != null) {
////        return true;
////      }
//    }
//    return false;
//  }
//}
