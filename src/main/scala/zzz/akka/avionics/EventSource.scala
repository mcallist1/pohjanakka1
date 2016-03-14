package zzz.akka.avionics

import akka.actor.{Actor, ActorRef}

object EventSource {
  // Messages used by listeners to register and unregister themselves:
  case class RegisterListener(listener: ActorRef)
  case class UnregisterListener(listener: ActorRef)
}

trait EventSource {
  def sendEvent[T](event: T): Unit
  def eventSourceReceive: Actor.Receive
}

trait ProductionEventSource extends EventSource { this: Actor =>
  import EventSource._

  var listeners = Vector.empty[ActorRef]

  // Send event to all registered listeners:
  def sendEvent[T](event: T): Unit = listeners foreach {
    _ ! event
  }

  // Partial function to handle messages for our event listeners. Any class that
  // mixes in this trait will have to include this method as part of its receive method:
  def eventSourceReceive: Receive = {
    case RegisterListener(listener) =>
      listeners = listeners :+ listener   //return new Vector with appended listener
    case UnregisterListener(listener) =>
      listeners = listeners.filter( _ != listener)
  }
}
