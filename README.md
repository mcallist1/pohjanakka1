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
You can find a repo on GitHub that has most of the source-code:
https://github.com/danluu/akka-concurrency-wyatt
...but anyway I am typing in all* the code from the print copy of the book, as that is the best way to learn. Also IMAO you can learn a lot about Scala (or many other languages) by using IntelliJ or Eclipse when typing in code, due to their nice "predictive input" and other features. I remember the days before Eclipse (and before Google-search/Stackoverflow), coding was much harder back then.

(* By "all", I'm excluding snippets copy-paste-modified from stackoverflow when stuck).

Note that since I didn't make the effort to structure this repo with e.g. one branch per chapter (at least not initially, maybe make a new branches from Ch9 onwards), the code here is just the current snapshot of how far I got through the book (of course you could look back through the commit history to master-branch, but I usually pushed changes because it was sleep-time, and not because I had completed a chapter). So it's better to code your code and look at this repo and danluu's repo for hints, and look at the debug-notes below.

---
Code-differences / any bugs I found between book publication date and spring 2016 (spring as in the season, not as in the popular DI-framework):

[1] the syntax "system.shutdown()" for ActorSystem is deprecated, for me it resulted in compile-time failure (also in non-IDE mode), but IDE's replacement-suggestion: "system.terminate()" works fine.

[2] ActorContext.actorFor - all variants of actorFor() are deprecated, so we have to replace them to keep the compiler happy. See Stackoverflow for useful comments, including an answer by Derek Wyatt:
http://stackoverflow.com/questions/22951549/how-do-you-replace-actorfor

Simplest workaround is to use actorSelection() instead... for example (in receive-method of class Pilot):
```
case ReadyToGo => 
...
      var copilotFuture: Future[ActorRef] = for (cop <- context.actorSelection("../" + copilotName).resolveOne()) yield cop
      copilot = Await.result(copilotFuture, 1.second)
      //println("copilot after Await.result: " + copilot)
```
There are some subtle things to consider here about Futures n so on (see stackoverflow-discussion), but anyway this substitution seems to work ok. Note that you will need to provide an implicit Timeout as well when using actorSelection(), e.g. in the Pilot class:
```implicit val timeout = Timeout(2.seconds)``` (and provide needed import-statements - a decent IDE will help you note and provide these (Eclipse has the excellent "Organise Imports", IntelliJ has reasonable support too); in this case you need ```import akka.util.Timeout``` and ```import scala.concurrent.duration._``` ). And the 2.seconds timeout value - I just picked 2 seconds, as a decision made in about, uhh, 2 seconds. 
Or, as in the example just now, you can use the "pattern" of: use for...yield to get a Future[ActorRef]... then use Await.result specifying a timeout explicitly. In this case you also need relevant imports: ```import scala.concurrent.{Await, Future}``` 

[3] The Autopilot class is not defined (at least i didn't see it defined anywhere in the book), so comment out the following line in Plane.scala (and anywhere else an Autopilot reference occurs:
```val autopilot = context.actorOf(Props[Autopilot], "Autopilot")```

[4] After working through Chapter7, if you try to run the app (via Avionics.main-method), it will probably fail with something like:
```
[error] (run-main-0) java.lang.ClassCastException: Cannot cast zzz.akka.avionics.Plane$Controls to akka.actor.ActorRef
java.lang.ClassCastException: Cannot cast zzz.akka.avionics.Plane$Controls to akka.actor.ActorRef
```
The reason that error occurs is because of the line in Avionics object:
```val control = Await.result( (plane ? Plane.GiveMeControl).mapTo[ActorRef], 5.seconds )```
which is Awaiting a result of type ActorRef, but is unable to do a .mapTo[ActorRef] on the non-ActorRef it is getting back from Plane.receive, which has just been changed to type Controls:
```
case GiveMeControl =>
      log info "Plane giving control..."
      sender ! Controls(controls) 
```

On the other hand, the book doesn't say "the app is still working", after all the refactoring continues into Chapter8. But if you want the app to run roughly as before, i.e. main method flies the plane for a few seconds, though now we also give our Pilot the controls for him/her to not use yet... 
...then the following ad-hoc modifications will get you past the runtime-exception:
In the Plane's companion object, add: ```case object GiveMainControl```
In the Plane's receive method, add: 
```
case GiveMainControl =>
         log info "Plane giving control to Main..."
         sender ! controls
```      
And replace the original val control assignment in Avionic's main method with:
 
```val control = Await.result( (plane ? Plane.GiveMainControl).mapTo[ActorRef], 5.seconds )```

"Pointless hacking!! You will be refactoring the code later anyway!!" Well yes, but I just like to avoid runtime exceptions... and you still get to see your Chapter7 debug-log output, so you proved that the runtime-exception wasn't due to the new Pilots...ok ok, ahl get me coat.
(Since Avionics-object is not an Actor, so refactoring it just so that it would be able to match case Controls(controlSurfaces) in a receive-method seems sub-ideal, although I suppose that would be a possibility).

[5] In Chapter8, when refactoring the Plane class, startPeople-method wants to use String variables when naming the Actors: pilot, copilot, (lead)attendant.
But in Chapter7, Plane class only defined ActorRefs here. So if we want to keep the ActorRefs, but make the code a bit neater, we can declare vals for pilotName, copilotName, attendantName, and use them in two places, like so:
```
  val pilotName = config.getString(s"$cfgstr.pilotName")
  val copilotName = config.getString(s"$cfgstr.copilotName")
  val attendantName = config.getString(s"$cfgstr.leadAttendantName")

  val pilot = context.actorOf(Props[Pilot], pilotName)
  val copilot = context.actorOf(Props[Copilot], copilotName)
  // val autopilot = context.actorOf(Props[Autopilot], "Autopilot")  //not implemented in book, at least not in Ch.7
  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), attendantName)

...

  def startPeople(): Unit = {
    val people = context.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          // These children get implicitly added to the hierarchy:
          context.actorOf(Props(newPilot), pilotName)
          context.actorOf(Props(newCopilot), copilotName)
        }
      }), "Pilots"
    )
    // Use default strategy for flight attendants i.e. restart indefinitely:
    context.actorOf(Props(newLeadFlightAttendant), attendantName)
    Await.result(people ? WaitForStart, 1.second)  // blocking ok for Plane start-up
  }
```  

That is a bit neater, and saves the print-book's implementation of startPeople as-is. (First three of those lines also shown in danluu's implementation, I copy-pasted them across).



Zettel (fold in or delete later)

( see  http://www.artima.com/forums/flat.jsp?forum=289&thread=349574 ), discussion about Plane errors etc.

http://doc.akka.io/docs/akka/current/scala/actors.html  , version 2.4.2 latest stable March 2016
