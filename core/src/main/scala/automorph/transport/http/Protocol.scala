package automorph.transport.http

/** Transport protocol. */
sealed abstract private[automorph] class Protocol(val name: String) {

  override def toString: String =
    name
}

/** Transport protocols. */
private[automorph] object Protocol {

  case object Http extends Protocol("HTTP")

  case object WebSocket extends Protocol("WebSocket")
}
