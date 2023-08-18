package automorph.transport.local

/**
 * Local RPC message context
 *
 * @param value
 *   context value
 */
final case class LocalContext(value: Any)

object LocalContext {

  /** Implicit default context value. */
  implicit val defaultContext: LocalContext = LocalContext {}
}
