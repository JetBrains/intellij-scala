# Reporting issues

Thank you for taking the time to report a problem. To make sure that your
problem will be solved in the shortest time possible, please check that your bug
report has:

- Information about operation system used
- Version of IDEA used (`Help -> About` dialog)
- Version of Scala plugin used (`File -> Settings -> Plugins` dialog)
- Detailed description of the steps one should take to reproduce this situation
- Problems you have encountered
- Behaviour you have expected

You could also speed up fixing a bug by providing the following (if possible):

- Example project which triggers this problem
- Logs, performance traces, etc.

We strongly recommend you to report bugs [straight to Youtrack](https://youtrack.jetbrains.com/issues/SCL#newissue). 
This is the main place where we store issues.

# Contributing

I you haven't do so yet, read the section on 
[setting-up-the-project in the readme](https://github.com/JetBrains/intellij-scala#setting-up-the-project).

Reading the [IDEA platform architectural overview](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview.html)
is strongly recommended before perusing the code. Unfortunately there is no
equivalent for the scala plugin itself at the moment but any of your questions
are welcome on [intellij-scala gitter](https://gitter.im/JetBrains/intellij-scala)
and we will be glad to answer them.

[PSI Viewer](https://www.jetbrains.com/help/idea/psi-viewer.html) is an essential tool to
dig into the structure of a source code.

Some guidelines for code, commits and pull requests:
- Make sure you have a YouTrack issue number before you start working.
- Reference the YouTrack issue number in your commits.
- In the last commit of a series which fixes an issue, add a line `#SCL-XXXXX fixed` for each YouTrack
  issue which is fixed. This will close the YouTrack issues automatically.
- Try to keep commits focused to help reviewers.
- Make sure your modifications are covered by tests.

Happy coding !
