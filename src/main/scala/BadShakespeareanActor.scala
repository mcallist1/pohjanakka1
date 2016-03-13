/**
  * Created by murmeister on 11.3.2016.
  */

import akka.actor.{Actor, Props, ActorSystem}

class BadShakespeareanActor extends Actor{
  //app-logic:
  def receive = {
    case "Good Morning" =>
      println("Him: Forsooth 'tis the 'morn, but mourneth " +
              "for thou dost I do!")
    case "You're terrible" =>
      println("Him: Aye well yer not wrong therr.")
  }
}

object BadShakespeareanMain {
  val system = ActorSystem("BadShakespearean")
  val actor = system.actorOf(Props[BadShakespeareanActor], "Shake")

  //a helper method to talk to our thespian:
  def send(msg: String): Unit = {
    println(s"Me:  $msg")
    actor ! msg
    Thread.sleep(100)
  }

  //the main method:
  def main(args: Array[String]): Unit = {
    send("Good Morning")
    send("You're terrible")
    system.terminate()
  }
}
