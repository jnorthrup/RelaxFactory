package rxf.server;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;

import one.xio.HttpHeaders;

import static one.xio.HttpMethod.UTF8;
import static rxf.server.BlobAntiPatternObject.COOKIE;
import static rxf.server.BlobAntiPatternObject.moveCaretToDoubleEol;

/**
 * long overdue utility class to parse a request header or response header according to declared need of header/cookie
 * <p/>
 * preload headers and cookies, send response and request initial onRead for .apply()
 * <p/>
 * User: jim
 * Date: 5/19/12
 * Time: 10:00 PM
 */
public class Rfc822HeaderState {
  public boolean dirty;
  public String[] headers = {};
  private String[] cookies = {};

  @Override
  public String toString() {
    return "Rfc822HeaderState{" +
        "dirty=" + dirty +
        ", headers=" + (headers == null ? null : Arrays.asList(headers)) +
        ", cookies=" + (cookies == null ? null : Arrays.asList(cookies)) +
        ", sourceRoute=" + sourceRoute +
        ", headerBuf=" + headerBuf +
        ", headerStrings=" + headerStrings +
        ", cookieStrings=" + cookieStrings +
        ", methodProtocol='" + methodProtocol + '\'' +
        ", pathRescode='" + pathRescode + '\'' +
        '}';
  }

  private InetAddress sourceRoute;


  private ByteBuffer headerBuf;
  private Map<String, String> headerStrings;
  private Map<String, String> cookieStrings;
  private String methodProtocol;
  private String pathRescode;

  public Rfc822HeaderState(String... headers) {

    this.headers = headers;
  }

  public Rfc822HeaderState sourceKey(SelectionKey key
  ) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();
    final InetAddress inetAddress1 = channel.socket().getInetAddress();
    final InetAddress inetAddress = sourceRoute = inetAddress1;
    return this;
  }

  public ByteBuffer getHeaderBuf() {
    return headerBuf;
  }

  public Map<String, String> getHeaderStrings() {
    return headerStrings;
  }

  public String getPathRescode() {
    return pathRescode;
  }

  public Map<String, String> getCookieStrings() {
    return cookieStrings;
  }

  List<String> getHeadersNamed(String header) {
    final String decode = UTF8.decode((ByteBuffer) getHeaderBuf().rewind()).toString();
    final String prefix = new StringBuilder().append(header).append(": ").toString();

    final String[] lines = decode.split("\n[^ \t]");
    Arrays.sort(lines);
    final ArrayList<String> a = new ArrayList<String>();
    for (String line : lines) {
      boolean trigger = false;
      if (line.startsWith(prefix))
        trigger = a.add(line.substring(prefix.length()));
      else if (!trigger) break;
    }
    return a;
  }


  public Rfc822HeaderState cookies(String... cookies) {
    this.cookies = cookies;
    final List<? extends String> headersNamed = getHeadersNamed(COOKIE);
    cookieStrings = new LinkedHashMap<String, String>();
    Arrays.sort(cookies);
    for (String cookie : headersNamed) {

      for (String s : cookie.split(";")) {
        final String[] split = s.split("^[^=]*=", 2);
        for (String s1 : split) {
          cookieStrings.put(split[0].trim(), split[1].trim());
        }
      }
//         final String[] split = cookie.split("^[^=]*=", 2);
//
//      final String[] split = cookie.split("^[^=]*=", 2);
//      final String tag = split[0];
//      if (Arrays.binarySearch(cookies, tag.trim()) > 0) {
//        final String value = split[1];
//        cookieStrings.put(tag, value);
//      }
    }
    return this;
  }

  public Rfc822HeaderState apply(ByteBuffer cursor) {
    if (!cursor.hasRemaining()) cursor.flip();
    final int anchor = cursor.position();
    final ByteBuffer slice1 = cursor.duplicate().slice();
    while (slice1.hasRemaining() && slice1.get() != ' ') ;
    methodProtocol = UTF8.decode((ByteBuffer) slice1.flip()).toString().trim();
    while (cursor.hasRemaining() && cursor.get() != ' ') ; //method/proto
    final ByteBuffer slice = cursor.slice();
    while (slice.hasRemaining() && slice.get() != ' ') ;
    pathRescode = UTF8.decode((ByteBuffer) slice.flip()).toString().trim();
    headerBuf = null;
    final boolean wantsCookies = cookies != null && cookies.length > 0;
    final boolean wantsHeaders = wantsCookies || headers.length > 0;
    headerBuf = (ByteBuffer) moveCaretToDoubleEol(cursor).duplicate().flip();
    headerStrings = null;
    cookieStrings = null;
    if (wantsHeaders) {
      Map<String, int[]> headerMap = HttpHeaders.getHeaders((ByteBuffer) headerBuf.rewind());
      headerStrings = new LinkedHashMap<String, String>();
      for (String o : headers) {
        int[] o1 = headerMap.get(o);
        if (null != o1)
          headerStrings.put(o, UTF8.decode((ByteBuffer) headerBuf.duplicate().clear().position(o1[0]).limit(o1[1])).toString().trim());
      }

    }
    return this;
  }

  public String getMethodProtocol() {
    return methodProtocol;
  }

  public void setMethodProtocol(String methodProtocol) {
    this.methodProtocol = methodProtocol;
  }

  public InetAddress getSourceRoute() {
    return sourceRoute;
  }

  public boolean isDirty() {
    return dirty;
  }

  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  public Rfc822HeaderState headers(String... headers) {
    this.headers = headers;
    return this;
  }
}

//}
//      rfc822HeaderState.apply((ByteBuffer) dst.flip());
//
//            BlobAntiPatternObject.moveCaretToDoubleEol(dst);
//            ByteBuffer[] headerBuf = {((ByteBuffer) dst.duplicate().flip()).slice()};
//            if (SendJsonVisitor.DEBUG_SENDJSON) {
//              System.err.println("result: " + UTF8.decode((ByteBuffer) headerBuf[0].rewind()));
//            }
//
//            int[] bounds = HttpHeaders.getHeaders((ByteBuffer) headerBuf[0].rewind()).get(CONTENT_LENGTH);
//            if (null == bounds) {
//
//              bounds = HttpHeaders.getHeaders((ByteBuffer) headerBuf[0].rewind()).get(TRANSFER_ENCODING);
//
//              if (null != bounds) {
//                key.selector().wakeup();
//                key.interestOps(OP_READ).attach(new ChunkedEncodingVisitor(dst, receiveBufferSize, channel, returnTo));
//
//              }//doChunked
//            } else {
//              total = Long.parseLong(UTF8.decode((ByteBuffer) dst.duplicate().limit(bounds[1]).position(bounds[0])).toString().trim());
//              remaining = total - dst.remaining();
//
//              ByteBuffer payload;
//              if (remaining <= 0) {
//                payload = dst.slice();
//                String rescode = rfc822HeaderState.getPathRescode();
//                BlobAntiPatternObject.returnJsonString(returnTo, key, rescode, payload);
//              } else {
//                final LinkedList<ByteBuffer> ll = new LinkedList<ByteBuffer>();
//                ll.add(dst.slice());
//                key.selector().wakeup();
//                key.interestOps(SelectionKey.OP_READ).attach(new Impl() {
//                  @Override
//                  public void onRead(SelectionKey key) throws InterruptedException, IOException, ExecutionException {
//                    ByteBuffer payload = ByteBuffer.allocateDirect(receiveBufferSize);
//                    int read = channel.read(payload);
//                    if (-1 == read) {
//                      key.channel().close();
//                      if ($DBG && RfPostWrapper.ORIGINS.containsKey(key)) {
//                        Rfc822HeaderState rfc822HeaderState = RfPostWrapper.ORIGINS.get(key);
//                        System.err.println("closing " + arrToString(rfc822HeaderState) + wheresWaldo());
//                      }
//                      return;
//                    }
//                    ll.add(payload);
//                    remaining -= read;
//                    if (0 == remaining) {
//                      payload = ByteBuffer.allocateDirect((int) total);
//                      ListIterator<ByteBuffer> iter = ll.listIterator();
//                      while (iter.hasNext()) {
//                        ByteBuffer buffer = iter.next();
//                        iter.remove();
//                        if (buffer.position() == total)
//                          payload = (ByteBuffer) buffer.flip();
//                        else
//                          payload.put(buffer);
//                      }
//                      BlobAntiPatternObject.returnJsonString(returnTo, key, rfc822HeaderState.getPathRescode(), payload);
//                    }
//                  }
//                });
//              }
//            }
//            return null;