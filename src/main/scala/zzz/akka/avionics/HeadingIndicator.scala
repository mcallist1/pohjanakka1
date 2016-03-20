package zzz.akka.avionics

import akka.actor.{Actor, ActorLogging}
import scala.concurrent.duration._

// Ch9 p222 exercise for the reader: wire up HeadingIndicator to plane with provider etc:
trait HeadingIndicatorProvider {
  def newHeadingIndicator: Actor = HeadingIndicator()
}

object HeadingIndicator {
  def apply(): Actor = new HeadingIndicator() with ProductionEventSource
  // Msg type for: change has occurred to how fast our bank(direction) is changing:
  case class BankChange(amount: Float)
  // Event to publish to listeners who want to know where we are heading:
  case class HeadingUpdate(heading: Double)
}

// murmelssonic TODO: book has this as a trait, but could it be a class instead?
// either works, but maybe trait is more consistent since we basically "implement itself". Not sure,
// need to maybe check the OVS-reference or sth. Using class for now:
//trait HeadingIndicator extends Actor with ActorLogging {
class HeadingIndicator extends Actor with ActorLogging {
  this: EventSource =>
  import HeadingIndicator._
  import context._
  // Internal message we use to recalculate our heading-direction:
  case object Tick
  // Maximum degrees-per-second our plane can bank-turn:
  val maxDegPerSec = 5
  // Timer for scheduling our updates:
  // akka.actor.Scheduler akkadoc:
  // final def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, receiver: ActorRef, message: Any)
  val ticker = system.scheduler.schedule(100.millis, 100.millis, self, Tick)
  // Initialise lastTick:
  var lastTick: Long = System.currentTimeMillis
  // current rate of bank-turn:
  var rateOfBank = 0f
  // current heading-direction:
  var heading = 0f

  def headingIndicatorReceive: Receive = {
    // Keeps rate of bank-change within range [-1, 1]:
    case BankChange(amount) =>
      rateOfBank = amount.min(1.0f).max(-1.0f)
      log info f"HeadingIndicator has set rate-of-bank to $rateOfBank%.2f."

    // Calculates our heading-delta based on current rate of bank-change, time-delta from previous calculation, and
    // max degrees per second bank-turn:
    case Tick =>
      val tick = System.currentTimeMillis
      val timeDelta = (tick - lastTick) / 1000f
      val degs = rateOfBank * maxDegPerSec
      heading = (heading + (360 + (timeDelta * degs))) % 360
      lastTick = tick
      // Send HeadingUpdate event to our listeners using our EventSource.sendEvent()-method:
      sendEvent(HeadingUpdate(heading))
  }

  // We are mixin' in our EventSource here, so we have to compose our receive()-partial-function accordingly:
  def receive = eventSourceReceive orElse headingIndicatorReceive

  // Cancel timer when we are shutting-down (terminating):
  override def postStop(): Unit = ticker.cancel()
}
