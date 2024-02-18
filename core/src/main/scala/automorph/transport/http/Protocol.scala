package automorph.transport.http

/** Transport protocol. */
sealed private[automorph] trait Protocol {

  /** Protocol name. */
  def name: String

  override def toString: String =
    name
}

/** Transport protocols. */
private[automorph] object Protocol {

  case object Http extends Protocol {
    override val name: String =
      "HTTP"
  }

  case object WebSocket extends Protocol {
    override val name: String =
      "WebSocket"
  }
}
