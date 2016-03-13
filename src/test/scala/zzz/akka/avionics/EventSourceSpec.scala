package zzz.akka.avionics

/**
  * Created by murmeister on 12.3.2016.
  */
import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestKit, TestActorRef, ImplicitSender}
import org.scalatest.{WordSpecLike, BeforeAndAfterAll}
import org.scalatest.Matchers

// We can't test a trait easily, so we create a specific EventSource-derived class which conforms
// to the trait's signature so that we can test production code for EventSource handling:
class TestEventSource extends Actor with ProductionEventSource {
  def receive = eventSourceReceive
}

// ame-of-testee-class>Spec is a naming convention
class EventSourceSpec extends TestKit(ActorSystem("EventSourceSpec"))
                      with WordSpecLike
                      with Matchers
                      with BeforeAndAfterAll {
  import EventSource._
  override def afterAll() { system.terminate() }
  "EventSource" should {
    "allow us to register a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.listeners should contain (testActor)
    }
    "allow us to unregister a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.receive(UnregisterListener(testActor))
      real.listeners.size should be (0)
    }
    "send the event to our test actor" in {
      val testA = TestActorRef[TestEventSource]
      testA ! RegisterListener(testActor)
      testA.underlyingActor.sendEvent("Fibonacci")
      expectMsg("Fibonacci")
    }
  }
}
