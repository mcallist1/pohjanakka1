Notes on working through the exercises of the book: "Akka Concurrency" (Derek Wyatt, Artima Press, 2013).

Rather than use giter8 and vim, neither of which i know much about, I prefer to use Lightbend Activator. ("Lightbend" is the new company name in early 2016 for The Artists Formerly Known as "Typesafe", I express no opinion (here) on the name-change).

Anyway you can download Activator from http://www.lightbend.com/activator/download

Then use the advice for getting an Akka project template/skeleton setup locally (various Options for doing this):
http://www.lightbend.com/activator/template/hello-akka

Option 1 (i.e. use Activator UI) is a good idea if you have never looked at Akka before, so in the "Activator Web IDE" you get a wee tutorial n everything.

Anyway I used Option 3 to get started:
```$ activator new pohjanakka1 hello-akka```

It's a good idea to use the "sbt run" and "sbt test" commands on your new project immediately, just to check the template is a working one. Those two commands you can use on your own code later, useful when you want to e.g. run some app, or do tests.

After that you can get rid of the template Scala files for Greeter-sample-app and so on, including the TestSpec files (and delete the Java files). Or you can keep them there for a while, only effect is that e.g. "sbt run" will ask you which codeset you want to run from your various executable classes.

You can code your Scala examples in project path: src/main/scala and your tests into src/test/scala .

I am using IntelliJ IDEA as the IDE, as it has a good Scala plugin, and good SBT support.

So after creating the project using Activator (as described above), I just opened the IDE and did a File | Open... | drill-down-to-project-root. You should open it as an SBT-project, since that is what it is.

I have IDEA set up to use custom SBT-launcher rather than the one shipped by IntelliJ as part of the IDE.

--
You can find a repo on GitHub that has most of the source-code, but anyway I am typing in all the code from the print copy of the book, as that is the best way to learn. Also IMAO you can learn a lot about Scala (or many other languages) by using IntelliJ or Eclipse when typing in code, due to their nice "predictive input" and other features. I remember the days before Eclipse (and before Google-search/Stackoverflow), coding was much harder back then.


The code-differences between publication date and March 2016:
the syntax "system.shutdown()" for ActorSystem is deprecated, for me it resulted in compile-time failure (also in non-IDE mode), but IDE's replacement-suggestion: "system.terminate()" works fine.

