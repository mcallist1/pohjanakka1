package zzz.akka.avionics

/**
  * Created by murmeister on 12.3.2016.
  */

import akka.actor.{Props, Actor, ActorRef, ActorLogging}

object Plane {
  // Returns control-surfaces of the Plane to the Actor that asks for them
  case object GiveMeControl
  // Response to GiveMeControl message:
  case class Controls(controls: ActorRef)
}

// The Plane has the controls, so someone can get the controls by sending a GiveMeControl message
// to the Plane. The Plane also has the Altimeter, so we build an Altimeter also and its ActorRef
// goes into our control-surface:
class Plane extends Actor with ActorLogging {
  import Altimeter._
  import Plane._
  import EventSource._

  val cfgstr = "zzz.akka.avionics.flightcrew"

  // Use Altimeter-companion-object's apply method to create the Actor:
  val altimeter = context.actorOf(Props(Altimeter()), "Altimeter")  // Altimeter's ActorRef now a child of Plane

  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)), "ControlSurfaces")

  val config = context.system.settings.config

  val pilot = context.actorOf(Props[Pilot], config.getString(s"$cfgstr.pilotName"))

  val copilot = context.actorOf(Props[Copilot], config.getString(s"$cfgstr.copilotName"))

  // val autopilot = context.actorOf(Props[Autopilot], "Autopilot")  //not implemented in book, at least not in Ch.7

  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), config.getString(s"$cfgstr.leadAttendantName"))

  override def preStart(): Unit = {
    altimeter ! RegisterListener(self)
    List(pilot, copilot) foreach { _ ! Pilots.ReadyToGo }
    //pilot ! Pilots.ReadyToGo
  }

  def receive = {
    case GiveMeControl =>
      log info "Plane giving control..."
      sender ! Controls(controls)
    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
  }
}
