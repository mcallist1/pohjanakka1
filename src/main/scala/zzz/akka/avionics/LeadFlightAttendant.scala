package zzz.akka.avionics

import akka.actor.{Actor, ActorRef, Props}

// Lead Attendant has a policy for gopher-Attendant construction:
trait AttendantCreationPolicy {
  // Can configure this, here some default spec:
  val numberOfAttendants: Int = 8
  def createAttendant: Actor =  FlightAttendant()
}

// A factory-mechanism for configuring how to create a Lead Attendant:
trait LeadFlightAttendantProvider {
  def newLeadFlightAttendant: Actor = LeadFlightAttendant()
}

object LeadFlightAttendant {
  case object GetFlightAttendant   //case object is like an Enum but with case matching etc
  case class Attendant(a: ActorRef)
  def apply() = new LeadFlightAttendant with AttendantCreationPolicy
}

class LeadFlightAttendant extends Actor {
  this: AttendantCreationPolicy =>
  import LeadFlightAttendant._
  // Once created, the LeadFlightAttendant creates its own team of minions:
  override def preStart(): Unit = {
    import scala.collection.JavaConverters._
    val attendantNames = context.system.settings.config.getStringList(
      "zzz.akka.avionics.flightcrew.attendantNames").asScala   //asScala makes it Iterable
    attendantNames take numberOfAttendants foreach( name => context.actorOf(Props(createAttendant), name))
  }
  // 'children' is an Iterable, so we can have a method that returns a random child from the sequence:
  def randomAttendant(): ActorRef = {
    context.children.take(scala.util.Random.nextInt(numberOfAttendants) +  1).last
  }
  def receive = {
    case GetFlightAttendant =>
      sender ! Attendant(randomAttendant())
    case m =>
      randomAttendant() forward m
  }
}

object FlightAttendantPathChecker {
  def main(args: Array[String]): Unit = {
    val system = akka.actor.ActorSystem("PlaneSimulation")
    val lead = system.actorOf(Props(new LeadFlightAttendant with AttendantCreationPolicy),
      system.settings.config.getString("zzz.akka.avionics.flightcrew.leadAttendantName"))
    Thread.sleep(2000)
    system.terminate()
  }
}
