package zzz.akka.avionics

/**
  * Updated code to get round deprecated ActorContext.actorFor syntax.
  * See also this discussion:
  * http://stackoverflow.com/questions/22951549/how-do-you-replace-actorfor
  */
import akka.actor.{Actor, ActorRef}
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

trait PilotProvider {
  def newPilot(plane: ActorRef, controls: ActorRef, altimeter: ActorRef): Actor = new Pilot(plane, controls, altimeter)
  def newCopilot(plane: ActorRef, altimeter: ActorRef): Actor = new Copilot(plane, altimeter)
  // def newAutopilot: Actor = new Autopilot
}

object Pilots {
  case object ReadyToGo
  case object RelinquishControl
}

/**
  * Dependency inject stuff the Pilot will need straight into its constructor:
  */
//class Pilot(plane: ActorRef, autopilot: ActorRef, var controls: ActorRef, altimeter: ActorRef) // Autopilot not implemented
class Pilot(plane: ActorRef, var controls: ActorRef, altimeter: ActorRef)
  extends Actor {
  import Pilots._
  import Plane._
  // timeout needed for using ActorContext.actorSelection:
  implicit val timeout = Timeout(2.seconds)

  //var controls: ActorRef = context.system.deadLetters   //Ch8 refactored into constructor

  var copilot: ActorRef = context.system.deadLetters
  //var autopilot: ActorRef = context.system.deadLetters   // Autopilot not implemented

  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      // context.parent ! GiveMeControl    // Ch8 refactored this out, as the new parent is not Plane but IsolatedStopSupervisor

      //copilot = context.actorFor("../" + copilotName)  //deprecated, use actorSelection instead:
      var cpl: ActorRef = context.system.deadLetters
      for (cop <- context.actorSelection("../" + copilotName).resolveOne()) yield cpl
      copilot = cpl
      //for (aut <- context.actorSelection("../Autopilot").resolveOne()) yield autopilot     // Autopilot not implemented

    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

class Copilot(plane: ActorRef, altimeter: ActorRef) extends Actor {
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
      //pilot = context.actorFor("../" + pilotName)  //deprecated, use actorSelection instead:
      var pil: ActorRef = context.system.deadLetters
      for (plt <- context.actorSelection("../" + pilotName).resolveOne()) yield pil
      pilot = pil
      //for (aut <- context.actorSelection("../Autopilot").resolveOne()) yield autopilot     // Autopilot not implemented
  }
}
