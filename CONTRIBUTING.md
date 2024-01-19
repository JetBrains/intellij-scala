# Contributing

Before proceeding please read section ["Setting up the project"](README.md#setting-up-the-project) if you haven't yet.

It's strongly recommended to get familiar with [IntelliJ Platform SDK documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html). \
It contains most of the information required to develop IntelliJ-based plugins.
In case of any questions you are welcome on our [discord channel](https://discord.gg/aUKpZzeHCK), we will be glad to answer your questions.

### Useful links:
- [How to contribute to IntelliJ Scala plugin](https://blog.jetbrains.com/scala/2016/04/21/how-to-contribute-to-intellij-scala-plugin/) (includes YouTube video)
- [Building IntelliJ IDEA plugins in Scala by Igal Tabachnik: Scala in the City Conference](https://www.youtube.com/watch?v=IPO-cY_giNA) (YouTube video)
- [Glossary of IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/glossary.html)
- [Explore the IntelliJ Platform API](https://plugins.jetbrains.com/docs/intellij/explore-api.html)
- [PSI Viewer](https://www.jetbrains.com/help/idea/psi-viewer.html) is an essential tool to dig into the structure of a source code.
- [Useful links in IntelliJ docs](https://plugins.jetbrains.com/docs/intellij/useful-links.html)

### Some guidelines for code, commits and pull requests:
- Make sure your modifications are covered by tests
- Make sure you have a YouTrack issue number before you start working
- Every commit message should reference the YouTrack issue number in format `#SCL-XXXXX`
  - Place primary issue number in the end of the first message line
  - If you have multiple equally-important related issues place them in the end of the first message line
  - If you have some non-primary, but related issues you can reference them in the commit message body.
  - In the last commit of a series which fixes an issue, append `#SCL-XXXXX fixed` to the end of the first message line
    issue which is fixed. This will close the YouTrack issues automatically when the change is merged into main branch.
- Use short subsystem prefix in your commits. We don't have a fixed set of such subsystems. Use common sense and, at least, the same prefix for a related group of commits
- Try to provide any extra helpful information in the commit message body
- Try to keep commits focused to help reviewers

#### Examples of commit messages:

> [sbt shell] delete SigIntAction #SCL-14369 #SCL-16727 #SCL-12030
>
> It's not actual after #SCL-15583:
> we now have a common shortcut for "Stop" action (see tooltip over the "stop" button)


> [annotator] dont annotate ConstructorInvocation in definitions (#SCL-20540, #SCL-20737) fixed
> 
> also related #SCL-20185

Happy coding !

### Writing tests
Please get familiar with docs [Testing Overview](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)

For most of the subsystems there are already some tests written
and all you need is to find an appropriate test class/base test class and add a new test case/test class.
However, quite frequently finding the suitable test class might be quite tricky.
In most cases there is no 1-1 correspondence between class name and test class names. \
There are multiple ways how to find the correct test suite:
1. Use "Go To Class" or "Go To File" action in combination with fuzzy search for the subsystem. \
   For example, if you search "ScalaDocFormatTest" you will find `ScalaDocFormatterTest.scala` and `ScalaDocFormatterTest2.scala`
2. Search for the edited test files in similar changes.\
   When you make changes in some files you can browse commit history of the files
   using [git blame](https://www.jetbrains.com/help/idea/investigate-changes.html#annotate_blame) or [file history](https://www.jetbrains.com/help/idea/investigate-changes.html#file-history). In that commit history you might see related test files. One of them might be the one you need.
3. If you struggle to find any suitable test you can ask for help in our [discord channel](https://discord.gg/aUKpZzeHCK)