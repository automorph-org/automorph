//package automorph.transport
//
//import automorph.RpcException.InvalidRequest
//import automorph.log.{LogProperties, Logging, MessageLog}
//import automorph.spi.{EffectSystem, RpcHandler}
//import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
//import automorph.util.Random
//import java.net.{InetSocketAddress, SocketAddress}
//import scala.collection.immutable.ListMap
//import scala.util.Try
//
///**
// * HTTP or WebSocket RPC request handler.
// *
// * @constructor
// *   Creates a HTTP or WebSocket RPC request handler.
// * @param receiveRequest
// *   function to receive a HTTP/WebSocket request
// * @param sendResponse
// *   function to send a HTTP/WebSocket response
// * @param protocol
// *   transport protocol
// * @param effectSystem
// *   effect system plugin
// * @param mapException
// *   maps an exception to a corresponding HTTP status code
// * @param rpcHandler
// *   RPC request handler
// * @tparam Effect
// *   effect type
// * @tparam Context
// *   RPC message context type
// * @tparam Request
// *   HTTP/WebSocket request type
// * @tparam Response
// *   HTTP/WebSocket response type
// * @tparam Connection
// *   HTTP/WebSocket connection type
// */
//final private[automorph] case class ClientServerHttpSender[
//  Effect[_],
//  Context <: HttpContext[?],
//  Request,
//  Response,
//  Connection,
//](
//  receiveRequest: Request => (Effect[Array[Byte]], HttpMetadata[Context]),
//  sendResponse: (Array[Byte], HttpMetadata[Context], Connection) => Effect[Response],
//  protocol: Protocol,
//  effectSystem: EffectSystem[Effect],
//  mapException: Throwable => Int,
//  rpcHandler: RpcHandler[Effect, Context],
//) extends Logging {
//  private val log = MessageLog(logger, protocol.name)
//  implicit private val system: EffectSystem[Effect] = effectSystem
//
//}
