package rxf.web.inf;

import one.xio.AsioVisitor.Impl;
import one.xio.HttpMethod;
import rxf.core.Rfc822HeaderState;
import rxf.core.Rfc822HeaderState.HttpRequest;
import rxf.core.Server;
import rxf.shared.PreRead;
import rxf.shared.KeepMatcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.channels.SelectionKey.OP_READ;
import static one.xio.HttpMethod.GET;
import static one.xio.HttpMethod.POST;
import static rxf.core.CouchNamespace.NAMESPACE;

/**
 * this class holds a protocol namespace to dispatch requests
 * <p/>
 * {@link rxf.core.CouchNamespace#NAMESPACE } is a map of http methods each containing an ordered map of regexes tested
 * in order of map insertion.
 * <p/>
 * User: jim Date: 4/18/12 Time: 12:37 PM
 */
public class ProtocolMethodDispatch extends Impl {

  public static final ByteBuffer NONCE = ByteBuffer.allocateDirect(0);
  public static final byte[] HEADER_TERMINATOR = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
  private static final boolean DEBUG_SENDJSON = false;

  /**
   * the PUT protocol handlers, only static for the sake of javadocs
   */
  public static Map<Pattern, Class<? extends Impl>> POSTmap = new LinkedHashMap<>();

  /**
   * the GET protocol handlers, only static for the sake of javadocs
   */
  public static Map<Pattern, Class<? extends Impl>> GETmap = new LinkedHashMap<>();

  static {
    NAMESPACE.put(POST, POSTmap);
    NAMESPACE.put(GET, GETmap);

    /**
     * for gwt requestfactory done via POST.
     * 
     * TODO: rf GET from query parameters
     */
    // guiced POSTmap.put(Pattern.compile("^/gwtRequest"), GwtRequestFactoryVisitor.class);

    /**
     * any url begining with /i is a proxied $req to couchdb but only permits image/* and text/*
     */

    // Pattern passthroughExpr = Pattern.compile("^/i(/.*)$");
    // retired GETmap.put(passthroughExpr, HttpProxyImpl.class/*(passthroughExpr)*/);

    /**
     * general purpose httpd static content couch that recognizes .gz and other compression suffixes when convenient
     * 
     * any random config mechanism with a default will suffice here to define the content root.
     * 
     * widest regex last intentionally system proprty: {value #RXF_CONTENT_ROOT}
     */
    GETmap.put(ContentRootCacheImpl.CACHE_PATTERN, ContentRootCacheImpl.class);
    GETmap.put(ContentRootNoCacheImpl.NOCACHE_PATTERN, ContentRootNoCacheImpl.class);
    GETmap.put(Pattern.compile(".*"), ContentRootImpl.class);
  }

  public static <T> String deepToString(T... d) {
    return Arrays.deepToString(d) + wheresWaldo();
  }

  public static String wheresWaldo(int... depth) {
    return Impl.wheresWaldo(depth);
  }

  public void onAccept(SelectionKey key) throws IOException {
    ServerSocketChannel channel = (ServerSocketChannel) key.channel();
    SocketChannel accept = channel.accept();
    accept.configureBlocking(false);
    Server.enqueue(accept, OP_READ, this);

  }

  public void onRead(SelectionKey key) throws Exception {
    SocketChannel channel = (SocketChannel) key.channel();
    ByteBuffer cursor = ByteBuffer.allocateDirect(4 << 10);
    int read = Helper.read(key, cursor);
    if (-1 == read) {
      ((SocketChannel) key.channel()).socket().close();// cancel();
      return;
    }

    HttpMethod method = null;
    HttpRequest httpRequest = null;
    try {
      // find the method to dispatch
      Rfc822HeaderState state = new Rfc822HeaderState().read((ByteBuffer) cursor.flip());
      httpRequest = state.$req();
      if (DEBUG_SENDJSON) {
        System.err.println(deepToString(StandardCharsets.UTF_8.decode((ByteBuffer) httpRequest
            .headerBuf().duplicate().rewind())));
      }
      String method1 = httpRequest.method();
      method = HttpMethod.valueOf(method1);
    } catch (Exception e) {
    }

    if (null == method) {
      ((SocketChannel) key.channel()).socket().close();// cancel();
      return;
    }

    Set<Entry<Pattern, Class<? extends Impl>>> entries = NAMESPACE.get(method).entrySet();
    String path = httpRequest.path();
    for (Entry<Pattern, Class<? extends Impl>> visitorEntry : entries) {
      Matcher matcher = visitorEntry.getKey().matcher(path);
      if (matcher.matches()) {
        if (DEBUG_SENDJSON) {
          System.err.println("+?+?+? using " + matcher.toString());
        }
        Class<? extends Impl> value = visitorEntry.getValue();
        Impl impl;

        impl = value.newInstance();
        boolean keepMatch = value.isAnnotationPresent(KeepMatcher.class);
        Object a[] =
            keepMatch ? new Object[] {impl, httpRequest, cursor, matcher.toMatchResult()}
                : new Object[] {impl, httpRequest, cursor};
        key.attach(a);
        if (value.isAnnotationPresent(PreRead.class))
          impl.onRead(key);
        key.selector().wakeup();
        return;
      }

    }
    System.err.println(deepToString("!!!1!1!!", "404", path, "using", NAMESPACE));
  }

}
