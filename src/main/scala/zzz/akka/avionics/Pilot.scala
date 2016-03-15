package zzz.akka.avionics

/**
  * Updated code to get round deprecated ActorContext.actorFor syntax. See e.g.:
  * https://github.com/danluu/akka-concurrency-wyatt/blob/master/src/main/scala/Plane.scala
  * See also this discussion:
  * http://stackoverflow.com/questions/22951549/how-do-you-replace-actorfor
  */
import akka.actor.{Actor, ActorRef}
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


object Pilots {
  case object ReadyToGo
  case object RelinquishControl
}

class Pilot extends Actor {
  import Pilots._
  import Plane._
  // timeout needed for using ActorContext.ActorSelection:
  implicit val timeout = Timeout(2.seconds)

  var controls: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters
  //var autopilot: ActorRef = context.system.deadLetters

  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      context.parent ! GiveMeControl
      //copilot = context.actorFor("../" + copilotName)  //deprecated, use actorSelection instead:
      for (cop <- context.actorSelection("../" + copilotName).resolveOne()) yield copilot
      //for (aut <- context.actorSelection("../Autopilot").resolveOne()) yield autopilot

    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

class Copilot extends Actor {
  import Pilots._
  // timeout needed for using ActorContext.ActorSelection:
  implicit val timeout = Timeout(2.seconds)

  var controls: ActorRef = context.system.deadLetters
  var pilot: ActorRef = context.system.deadLetters
  //var autopilot: ActorRef = context.system.deadLetters

  val pilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.pilotName")

  def receive = {
    case ReadyToGo =>
      //copilot = context.actorFor("../" + copilotName)  //deprecated, use actorSelection instead:
      for (cop <- context.actorSelection("../" + pilotName).resolveOne()) yield pilot
      //for (aut <- context.actorSelection("../Autopilot").resolveOne()) yield autopilot
  }
}
