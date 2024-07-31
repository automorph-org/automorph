//package automorph.transport.server;
//
//import automorph.transport.client.JettyClient;
//import org.eclipse.jetty.websocket.api.Callback;
//import org.eclipse.jetty.websocket.api.Session;
//
//import java.nio.ByteBuffer;
//
///*
//  Required because Jetty uses reflection to discover listener methods
//  and checks if the declaring class of these methods is Session.Listener.
//  This is not the case when implementing the Listener interface in Scala 3.
// */
//public class WebSocketListener implements Session.Listener.AutoDemanding {
//  private final JettyClient.FrameListener<?> frameListener;
//
//  public WebSocketListener(JettyClient.FrameListener<?> frameListener) {
//    this.frameListener = frameListener;
//  }
//
//  @Override
//  public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
//    frameListener.onWebSocketBinary(payload, callback);
//  }
//
//  @Override
//  public void onWebSocketText(String message) {
//    frameListener.onWebSocketText(message);
//  }
//
//  @Override
//  public void onWebSocketError(Throwable cause) {
//    frameListener.onWebSocketError(cause);
//  }
//
//  @Override
//  public void onWebSocketClose(int statusCode, String reason) {
//    frameListener.onWebSocketClose(statusCode, reason);
//  }
//}
