object PortOperations {
  import java.io.IOException
  import java.net.ServerSocket

  //Checks if local port is available
  def isPortAvailable(portNr: Int): Int = {
    var ignored: ServerSocket = null
    var port = -1
    try{
      ignored = new ServerSocket(portNr)
      port = ignored.getLocalPort
    }
    catch {
      case _: Exception => throw new Exception(s"port $portNr is not available")
    }
    finally {
      if (ignored != null) ignored.close()
    }
    port
  }

}
