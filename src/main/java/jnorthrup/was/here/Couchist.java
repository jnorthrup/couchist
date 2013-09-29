package jnorthrup.was.here;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import one.xio.AsioVisitor;
import one.xio.HttpMethod;
import rxf.server.CouchTx;
import rxf.server.driver.RxfBootstrap;
import rxf.server.gen.CouchDriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: jim
 * Date: 9/29/13
 * Time: 1:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class Couchist {

    public static final Pattern COMPILE = Pattern.compile("/([0-9]{5,})");
    public static final String TESTATTACHMENTS = RxfBootstrap.getVar("testattachments");

    public static void main(String[] args) throws IOException {
        new Thread() {
            @Override
            public void run() {
                try {


                    HttpMethod.init(new AsioVisitor() {
                        @Override
                        public void onRead(SelectionKey selectionKey) throws Exception {

                        }

                        @Override
                        public void onConnect(SelectionKey selectionKey) throws Exception {
                        }

                        @Override
                        public void onWrite(SelectionKey selectionKey) throws Exception {
                        }

                        @Override
                        public void onAccept(SelectionKey selectionKey) throws Exception {
                        }
                    });
                } catch (IOException e) {

                }
            }
        }.start();
        try {
            CouchDriver.DbCreate.$().db("table1").to().fire().tx();
            CouchDriver.DbCreate.$().db("table2").to().fire().tx();
        } finally {
        }

        Matcher matcher = COMPILE.matcher(args[0]);
        if (!matcher.find()) System.exit(1);
        String gistHash = matcher.group(1);
        String revision = CouchDriver.RevisionFetch.$().db("table1").docId(gistHash).to().fire().json();
        if (null != revision) System.exit(0);
        String req = "https://api.github.com/gists/" + gistHash;
        final TreeMap<  String,   TreeMap<String, String>> att = new TreeMap<>();
        try {
            try (InputStream in = new URL(req).openStream(); InputStreamReader inputStreamReader = new InputStreamReader(in)) {
                Map originalGist = new Gson().fromJson(inputStreamReader, Map.class);
//                 System.err.println("" + originalGist);
                if (originalGist.containsKey("files")) {
                    Map<String, Map> files1 = (Map<String, Map>) originalGist.get("files");
                    for (Map.Entry<String, Map> thefile : files1.entrySet()) {

                        Map<String, ?> fmap = thefile.getValue();

                        String raw_url = (String) fmap.get("raw_url");
                        String filename = (String) fmap.get("filename");
                        final Long size = ((Number) fmap.get("size")).longValue();
                        byte[] content = ((String) fmap.get("content")).getBytes(HttpMethod.UTF8);
                        String type = (String) fmap.get("type");
                        if (null!= TESTATTACHMENTS || content.length != size) {//not sure if github guarantees content in map, so just in case this is the fun way to get what's important.

                            HttpURLConnection urlConnection = (HttpURLConnection) new URL(raw_url).openConnection();
                            final int responseCode = urlConnection.getResponseCode();
                            if (200 == responseCode) {

                                type = urlConnection.getHeaderField("Content-Type");
                                try (InputStream inputStream = urlConnection.getInputStream()) {
                                    content = ByteStreams.toByteArray(inputStream);
                                }
                            }

                        }
                        final String b64content = BaseEncoding.base64().encode(content);
                        //                        http://wiki.apache.org/couchdb/HTTP_Document_API#Inline_Attachments

                        /**
                         * {
                         "_id":"attachment_doc",
                         "_attachments":
                         {
                         "foo.txt":
                         {
                         "content_type":"text\/plain",
                         "data": "VGhpcyBpcyBhIGJhc2U2NCBlbmNvZGVkIHRleHQ="
                         }
                         }
                         }
                         */
                        final TreeMap<String, String> inline = new TreeMap< >();
                        inline.put("content_type", type);
                        inline.put("data", b64content);
                        att.put(filename, inline);
                    }
                    TreeMap<Object, Object> doc = new TreeMap<>();
                    doc.put("_attachments", att)        ;
                    doc.put("_id", gistHash);
                    doc.put("orig", originalGist)  ;

                    final CouchTx table1 = CouchDriver.DocPersist.$().db("table1").validjson(new Gson().toJson(doc)).to().fire().tx();
                    System.err.println("results: "+table1);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
        }
        System.exit(0);
    }


}

