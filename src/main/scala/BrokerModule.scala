import org.zeromq.{SocketType, ZMQ}

import scala.collection.mutable

object BrokerModule {
  var ctx: ZMQ.Context = null
  var frontend: ZMQ.Socket = null
  var backend: ZMQ.Socket = null
  var poller: ZMQ.Poller = null
  var threadBroker: Thread = null
  private var _portFrontend = -1
  private var _portBackend = -1

  def getPortFrontend = _portFrontend

  def getPortBackend = _portBackend

  def enginesToClients: mutable.Map[String, String] = mutable.Map()

  def clientsToEngines: mutable.Map[String, String] = mutable.Map()
}

class BrokerModule(portFrontend: Int, portBackend: Int, host: String) extends java.lang.AutoCloseable {

  import BrokerModule.*

  def init() = {
    if ctx == null then
      println("Initialising broker")
      ctx = ZMQ.context(1)
      frontend = ctx.socket(SocketType.ROUTER)
      backend = ctx.socket(SocketType.ROUTER)
      println("Broker: starting frontend router socket on " + portFrontend.toString)
      frontend.bind(s"tcp://$host:${portFrontend.toString}")
      println("Broker: starting backend router socket on " + portBackend.toString)
      frontend.bind(s"tcp://$host:${portBackend.toString}")
      _portBackend = portBackend
      _portFrontend = portFrontend
      println("Initialising poller")
      poller = ctx.poller(2)
      poller.register(backend, ZMQ.Poller.POLLIN)
      poller.register(frontend, ZMQ.Poller.POLLIN)
      println("initialised brocker")
  }

  def start() = {
    init()
    if threadBroker != null then
      println("Starting broker thread")
      threadBroker = new Thread(new BrokerMainRunnable, "BrokerMainThread")
      threadBroker.start()
  }

  override def close() = {
    println("Broker: Executing close")
    if (threadBroker != null && threadBroker.isAlive())
      threadBroker.interrupt()
      println("Waiting while broker thread is alive")
      try {
        threadBroker.join();
      }
      catch
        case _: InterruptedException => System.out.printf("%s has been interrupted", threadBroker.getName())
    println("server: Broker was shutdown")
  }
}

class BrokerMainRunnable extends Runnable {

  import BrokerModule.*

  override def run(): Unit = {
    val NOFLAGS = 0
    println("Broker thread was started")
    try {
      while (!Thread.currentThread().isInterrupted()) {
        val rc = poller.poll
        if (rc == -1) throw Exception("brake")

        //Receive messages from engines
        if (poller.pollin(0)) {
          //FOR PROTOCOL SEE BOOK OReilly ZeroMQ Messaging for any applications 2013 ~page 100
          val workerAddr = backend.recv(NOFLAGS)
        }

      }
    }
    catch {
      case e: Throwable => println("Broker main thread: Got Exception: " + e.getMessage)
    }

    if (backend != null) {
      println("Broker: closing backend")
      backend.close()
    }
    if frontend != null then
      println("Broker: closing frontend")
      frontend.close()

    if poller != null then {
      println("Broker: close poll")
      poller.close()
    }

    if ctx != null then {
      println("Broker: terminate context")
      ctx.term()
    }
  }
}
