package zzz.akka.avionics

/**
  * Created by murmelssonic on 12.3.2016.
  */

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import zzz.akka.avionics.IsolatedLifeCycleSupervisor.WaitForStart
import akka.pattern.ask
import com.sun.org.apache.xml.internal.security.signature.MissingResourceFailureException
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Plane {

  // Returns control-surfaces of the Plane to the Actor that asks for them
  case object GiveMeControl

  // Ch7 workaround allowing Avionics main method to still request controls without Controls-wrapper:
  case object GiveMainControl

  // Response to GiveMeControl message:
  case class Controls(controls: ActorRef)

  def apply() = new Plane with AltimeterProvider
                          with PilotProvider
                          with LeadFlightAttendantProvider
                          with HeadingIndicatorProvider
}

// The Plane has the controls, so someone can get the controls by sending a GiveMeControl message
// to the Plane. The Plane also has the Altimeter, so we build an Altimeter also and its ActorRef
// goes into our control-surface:
class Plane extends Actor with ActorLogging {
  this: AltimeterProvider
    with PilotProvider
    with LeadFlightAttendantProvider
    with HeadingIndicatorProvider =>

  import Altimeter._
  import EventSource._
  import Plane._
  import scala.concurrent.ExecutionContext.Implicits.global

  val cfgstr = "zzz.akka.avionics.flightcrew"

  // Use Altimeter-companion-object's apply method to create the Actor:  //gets refactored away in Ch8 ??
  //val altimeter = context.actorOf(Props(Altimeter()), "Altimeter") // Altimeter's ActorRef now a child of Plane
  //val controls = context.actorOf(Props(new ControlSurfaces(altimeter)), "ControlSurfaces")


  val config = context.system.settings.config

  val pilotName = config.getString(s"$cfgstr.pilotName")
  val copilotName = config.getString(s"$cfgstr.copilotName")
  val attendantName = config.getString(s"$cfgstr.leadAttendantName")

  //val pilot = context.actorOf(Props[Pilot], pilotName)     //gets refactored away in Ch8 ??
  //println("PlaneB")
  //val copilot = context.actorOf(Props[Copilot], copilotName)    //gets refactored away in Ch8 ??

  // val autopilot = context.actorOf(Props[Autopilot], "Autopilot")  //not implemented in book, at least not in Ch.7

  //val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), attendantName)  //gets refactored away in Ch8 ??

  // Need an implicit Timeout when using ask ("?") method. And also for awaiting result of ActorSelection Future:
  implicit val askTimeout = Timeout(1.second)

  // We can wait around for a result, so long as all the calls to actorForControls and actorForPilots are being made
  // during application set up or other occasional message, this is ok. Currently these methods are called by:
  // preStart() (and first by sub-call also to startPeople());
  // when responding to messages: GiveMainControl, GiveMeControl
  def actorForControls(name: String) = {
    val equipmentActorFuture: Future[ActorRef] = for (eaf <- context.actorSelection("Equipment/" + name).resolveOne()) yield eaf
    val controlsActor: ActorRef = Await.result(equipmentActorFuture, 1.second)
    //println("controlsActor: " + controlsActor.toString())
    controlsActor
  }

  def actorForPilots(name: String) = {
    val pilotActorFuture: Future[ActorRef] = for (paf <- context.actorSelection("Pilots/" + name).resolveOne()) yield paf
    val pilotActor: ActorRef = Await.result(pilotActorFuture, 1.second)
    //println("pilotActor: " + pilotActor.toString())
    pilotActor
  }

  def startEquipment(): Unit = {
    val controls = context.actorOf(
      Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          val alt = context.actorOf(Props(newAltimeter), "Altimeter")
          val headInd =  context.actorOf(Props(newHeadingIndicator), "HeadingIndicator")  //Ch9
          // These children get implicitly added to the hierarchy:
          // context.actorOf(Props(newAutopilot), "Autopilot")  // Autopilot not implemented
          context.actorOf(Props(new ControlSurfaces(alt, headInd)), "ControlSurfaces")
        }
      }), "Equipment"
    )
    Await.result(controls ? WaitForStart, 1.second) // blocking (Await) is ok for Plane start-up
  }

  def startPeople(): Unit = {
    val plane = self
    // We depend on the Actor structure beneath the Plane by using actorSelection().
    // Hopefully this is change-resilient, since we'll be the ones making the changes...
    val controls = actorForControls("ControlSurfaces")
    // val autopilot = actorForControls("Autopilot")    // Autopilot not implemented
    val altimeter = actorForControls("Altimeter")
    val headInd = actorForControls("HeadingIndicator")  //Ch9
    val people = context.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          // These children get implicitly added to the hierarchy:

          context.actorOf(Props(newPilot(plane, controls, altimeter)), pilotName)

          context.actorOf(Props(newCopilot(plane, altimeter)), copilotName)

          //context.actorOf(Props(newPilot(plane, autopilot, controls, altimeter)), pilotName)   // Autopilot not implemented
          //context.actorOf(Props(newCopilot(plane, autopilot, altimeter)), copilotName)         // Autopilot not implemented
        }
      }), "Pilots"
    )
    // Use default strategy for flight attendants i.e. restart indefinitely:
    context.actorOf(Props(newLeadFlightAttendant), attendantName)
    Await.result(people ? WaitForStart, 1.second)  // blocking ok for Plane start-up
  }

  override def preStart(): Unit = {
    import EventSource.RegisterListener
    import Pilots.ReadyToGo

    // Get the children started. Starting order matters:
    startEquipment()
    startPeople()
    // Bootstrap the rest of the ActorSystem:
    actorForControls("Altimeter") ! RegisterListener(self)
    actorForControls("HeadingIndicator") ! RegisterListener(self)  //Ch9
    //println("HelloPlane.preStartA")
    actorForPilots(pilotName) ! ReadyToGo
    actorForPilots(copilotName) ! ReadyToGo
  }

  def receive = {
    case GiveMainControl =>
      log info "Plane giving control to Main..."
      //sender ! controls
      sender ! actorForControls("ControlSurfaces")
    case GiveMeControl =>
      log info "Plane giving control..."
      //sender ! Controls(controls)
      sender ! Controls(actorForControls("ControlSurfaces"))
    case AltitudeUpdate(altitude) =>
      log.info(f"Altitude is now: $altitude%.2f")
    case HeadingUpdate(heading) =>
      log.info(f"Now heading in direction: $heading%.2f")
  }
}
