package zzz.akka.avionics

/**
  * Created by murmeister on 12.3.2016.
  */

import akka.actor.{Props, Actor, ActorLogging}

object Plane {
  // Returns control-surfaces of the Plane to the Actor that asks for them
  case object GiveMeControl
}

// The Plane has the controls, so someone can get the controls by sending a GiveMeControl message
// to the Plane. The Plane also has the Altimeter, so we build an Altimeter also and its ActorRef
// goes into our control-surface:
class Plane extends Actor with ActorLogging {
  import Altimeter._
  import Plane._
  import EventSource._

  // Use Altimeter-companion-object's apply method to
  val altimeter = context.actorOf(Props(Altimeter()), "Altimeter")  // Altimeter's ActorRef now a child of Plane
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)), "ControlSurfaces")

  override def preStart(): Unit = {
    altimeter ! RegisterListener(self)
  }

  def receive = {
    case GiveMeControl =>
      log info "Plane giving control..."
      sender ! controls
    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
  }
}
